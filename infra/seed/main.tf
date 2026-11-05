# The foundation everything else stands on: one project, the Terraform state bucket and keyless GitHub login.
#
# Applied once by a person (docs/INFRASTRUCTURE.md, "First run"). After that it runs in GitHub Actions like every
# other module: tf-deploy owns the project, so it can change all of this itself. No service-account keys exist.

resource "google_project" "this" {
  name            = "Vesper"
  project_id      = var.project_id
  org_id          = var.org_id
  billing_account = var.billing_account

  # No default VPC (it ships with open SSH/RDP rules); nothing here needs a network.
  auto_create_network = false
  deletion_policy     = "PREVENT"
}

resource "google_project_service" "seed" {
  for_each = toset([
    "serviceusage.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "cloudbilling.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "sts.googleapis.com",
    "storage.googleapis.com",
  ])

  project            = google_project.this.project_id
  service            = each.value
  disable_on_destroy = false
}

# Terraform state for every module (prefixes seed/, gcp/, github/). Versioned, so a bad apply can be rolled back.
resource "google_storage_bucket" "tfstate" {
  project                     = google_project.this.project_id
  name                        = "${var.project_id}-tfstate"
  location                    = var.region
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = false

  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      num_newer_versions = 20
      with_state         = "ARCHIVED"
    }
    action {
      type = "Delete"
    }
  }

  lifecycle {
    prevent_destroy = true
  }

  depends_on = [google_project_service.seed]
}

# --- Identities used by GitHub Actions ---

# Applies on merge to main. Owner of this one project, so new resources never need a human to grant roles.
resource "google_service_account" "deploy" {
  project      = google_project.this.project_id
  account_id   = "tf-deploy"
  display_name = "Terraform apply (GitHub Actions, environment ${var.deploy_environment})"

  depends_on = [google_project_service.seed]
}

resource "google_project_iam_member" "deploy_owner" {
  project = google_project.this.project_id
  role    = "roles/owner"
  member  = "serviceAccount:${google_service_account.deploy.email}"
}

# Budgets live on the billing account, not the project.
resource "google_billing_account_iam_member" "deploy" {
  for_each = toset(["roles/billing.costsManager", "roles/billing.viewer"])

  billing_account_id = var.billing_account
  role               = each.value
  member             = "serviceAccount:${google_service_account.deploy.email}"
}

# Plans on pull requests. Read-only.
resource "google_service_account" "plan" {
  project      = google_project.this.project_id
  account_id   = "tf-plan"
  display_name = "Terraform plan, read-only (GitHub Actions, pull requests)"

  depends_on = [google_project_service.seed]
}

resource "google_project_iam_member" "plan" {
  for_each = toset([
    "roles/viewer",
    "roles/iam.securityReviewer",
    "roles/serviceusage.serviceUsageConsumer", # quota project for Firebase and budget reads
    "roles/firebase.viewer",
    "roles/identityplatform.viewer",
    "roles/datastore.viewer",
    "roles/cloudfunctions.viewer",
    "roles/run.viewer",
    "roles/serviceusage.apiKeysViewer",
    "roles/monitoring.viewer",
    "roles/logging.viewer",
    "roles/storage.objectViewer", # Terraform state and function sources
  ])

  project = google_project.this.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.plan.email}"
}

# Bucket metadata and IAM (refresh of the buckets); not part of the predefined viewer roles.
resource "google_project_iam_custom_role" "plan_bucket_reader" {
  project     = google_project.this.project_id
  role_id     = "planBucketReader"
  title       = "Plan: read bucket metadata"
  permissions = ["storage.buckets.get", "storage.buckets.getIamPolicy"]

  depends_on = [google_project_service.seed]
}

resource "google_project_iam_member" "plan_bucket_reader" {
  project = google_project.this.project_id
  role    = google_project_iam_custom_role.plan_bucket_reader.name
  member  = "serviceAccount:${google_service_account.plan.email}"
}

resource "google_billing_account_iam_member" "plan" {
  billing_account_id = var.billing_account
  role               = "roles/billing.viewer"
  member             = "serviceAccount:${google_service_account.plan.email}"
}

# --- Keyless login from GitHub Actions (Workload Identity Federation) ---

resource "google_iam_workload_identity_pool" "github" {
  project                   = google_project.this.project_id
  workload_identity_pool_id = "github"
  display_name              = "GitHub Actions"

  depends_on = [google_project_service.seed]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  project                            = google_project.this.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github"
  display_name                       = "GitHub OIDC"

  attribute_mapping = {
    "google.subject"          = "assertion.sub"
    "attribute.repository_id" = "assertion.repository_id"
  }

  # Only this repository. The numeric ID can't be taken over by a renamed or re-created repo.
  attribute_condition = "assertion.repository_id == \"${var.github_repository_id}\""

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

# Deploy: only jobs that run in the GitHub environment (main branch only, set in infra/github).
resource "google_service_account_iam_member" "wif_deploy" {
  service_account_id = google_service_account.deploy.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principal://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/subject/repo:${var.github_repository}:environment:${var.deploy_environment}"
}

# Plan: any job of this repository. Fork pull requests get no OIDC token, so they can't use it.
resource "google_service_account_iam_member" "wif_plan" {
  service_account_id = google_service_account.plan.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository_id/${var.github_repository_id}"
}
