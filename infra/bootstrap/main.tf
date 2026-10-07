# One-time foundation: projects, billing link, Workload Identity Federation and the deploy service account.
# No service-account keys are ever created; GitHub Actions authenticates through WIF.

resource "google_project" "env" {
  for_each = var.environments

  name            = "Stay Focused ${each.key}"
  project_id      = each.value.project_id
  org_id          = var.org_id != "" ? var.org_id : null
  billing_account = var.billing_account

  # No default VPC (it ships with open SSH/RDP rules); this foundation has no network workloads.
  auto_create_network = false

  # Terraform must not delete a project by accident.
  deletion_policy = "PREVENT"
}

resource "google_project_service" "bootstrap" {
  for_each = {
    for pair in setproduct(keys(var.environments), [
      "serviceusage.googleapis.com",
      "cloudresourcemanager.googleapis.com",
      "iam.googleapis.com",
      "iamcredentials.googleapis.com",
      "sts.googleapis.com",
    ]) : "${pair[0]}/${pair[1]}" => { env = pair[0], service = pair[1] }
  }

  project            = google_project.env[each.value.env].project_id
  service            = each.value.service
  disable_on_destroy = false
}

resource "google_service_account" "deploy" {
  for_each = var.environments

  project      = google_project.env[each.key].project_id
  account_id   = "tf-deploy"
  display_name = "Terraform deploy (GitHub Actions, ${each.key})"

  depends_on = [google_project_service.bootstrap]
}

# Read-only identity for pull-request plans (any ref of this repository). It can only read service state; later infra stories add the read roles their resources need.
resource "google_service_account" "plan" {
  for_each = var.environments

  project      = google_project.env[each.key].project_id
  account_id   = "tf-plan"
  display_name = "Terraform plan, read-only (GitHub Actions, ${each.key})"

  depends_on = [google_project_service.bootstrap]
}

resource "google_project_iam_member" "plan" {
  for_each = var.environments

  project = google_project.env[each.key].project_id
  role    = "roles/serviceusage.serviceUsageViewer"
  member  = "serviceAccount:${google_service_account.plan[each.key].email}"
}

resource "google_project_iam_member" "deploy" {
  for_each = {
    for pair in setproduct(keys(var.environments), var.deploy_roles) :
    "${pair[0]}/${pair[1]}" => { env = pair[0], role = pair[1] }
  }

  project = google_project.env[each.value.env].project_id
  role    = each.value.role
  member  = "serviceAccount:${google_service_account.deploy[each.value.env].email}"
}

resource "google_iam_workload_identity_pool" "github" {
  for_each = var.environments

  project                   = google_project.env[each.key].project_id
  workload_identity_pool_id = "github"
  display_name              = "GitHub Actions"

  depends_on = [google_project_service.bootstrap]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  for_each = var.environments

  project                            = google_project.env[each.key].project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github[each.key].workload_identity_pool_id
  workload_identity_pool_provider_id = "github"
  display_name                       = "GitHub OIDC"

  attribute_mapping = {
    "google.subject"          = "assertion.sub"
    "attribute.repository_id" = "assertion.repository_id"
    # repository id + ref, so the deploy identity can be limited to one branch.
    "attribute.repo_ref" = "assertion.repository_id + \":\" + assertion.ref"
  }

  # Only this repository can use the provider. The numeric id is immutable; names can be reused after a rename.
  attribute_condition = "assertion.repository_id == \"${var.github_repository_id}\""

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

# Deploy (apply) identity: only workflows running on the deploy branch of this repository.
resource "google_service_account_iam_member" "wif_deploy" {
  for_each = var.environments

  service_account_id = google_service_account.deploy[each.key].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github[each.key].name}/attribute.repo_ref/${var.github_repository_id}:refs/heads/${var.deploy_branch}"
}

# Plan identity: any ref of this repository (pull requests), read-only.
resource "google_service_account_iam_member" "wif_plan" {
  for_each = var.environments

  service_account_id = google_service_account.plan[each.key].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github[each.key].name}/attribute.repository_id/${var.github_repository_id}"
}
