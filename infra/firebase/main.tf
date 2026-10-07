# Empty environment: only the base APIs. Later stories add resources milestone by milestone
# (M6-01 Auth/Firestore, M7-01/02 rules and Functions, M9-03 AI Logic, ...).

locals {
  base_apis = [
    "serviceusage.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "iam.googleapis.com",
    "firebase.googleapis.com",
  ]
}

resource "google_project_service" "base" {
  for_each = toset(local.base_apis)

  project = var.project_id
  service = each.value

  # Keep APIs enabled if this resource is ever removed from config.
  disable_on_destroy = false
}
