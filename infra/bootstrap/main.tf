# One-time foundation: projects, billing link, Workload Identity Federation and the deploy service account.
# No service-account keys are ever created; GitHub Actions authenticates through WIF.

resource "google_project" "env" {
  for_each = var.environments

  name            = "Stay Focused ${each.key}"
  project_id      = each.value.project_id
  org_id          = var.org_id != "" ? var.org_id : null
  billing_account = var.billing_account

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
    "google.subject"       = "assertion.sub"
    "attribute.repository" = "assertion.repository"
    "attribute.ref"        = "assertion.ref"
  }

  # Only this repository can use the provider.
  attribute_condition = "assertion.repository == \"${var.github_repository}\""

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

resource "google_service_account_iam_member" "wif" {
  for_each = var.environments

  service_account_id = google_service_account.deploy[each.key].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github[each.key].name}/attribute.repository/${var.github_repository}"
}
