# Dev environment resources. Later stories add more milestone by milestone
# (M7-01/02 rules and Functions, M9-03 AI Logic, ...).

locals {
  base_apis = [
    "serviceusage.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "iam.googleapis.com",
    "firebase.googleapis.com",
    "identitytoolkit.googleapis.com",
    "billingbudgets.googleapis.com",
  ]

  apps = {
    app  = { package = "com.dculus.stayfocused", name = "Stay Focused" }
    kids = { package = "com.dculus.stayfocused.kids", name = "Stay Focused Kids" }
  }
}

resource "google_project_service" "base" {
  for_each = toset(local.base_apis)

  project = var.project_id
  service = each.value

  # Keep APIs enabled if this resource is ever removed from config.
  disable_on_destroy = false
}

resource "google_firebase_project" "this" {
  provider = google-beta
  project  = var.project_id

  depends_on = [google_project_service.base]
}

resource "google_firebase_android_app" "app" {
  provider = google-beta
  for_each = local.apps

  project      = google_firebase_project.this.project
  display_name = each.value.name
  package_name = each.value.package

  # Certificate fingerprints (debug and release). Public values passed in as variables.
  sha1_hashes   = var.sha1_fingerprints[each.key]
  sha256_hashes = var.sha256_fingerprints[each.key]

  # Terraform removes the app on destroy instead of only abandoning it.
  deletion_policy = "DELETE"
}

data "google_firebase_android_app_config" "this" {
  provider = google-beta
  for_each = google_firebase_android_app.app

  project = var.project_id
  app_id  = each.value.app_id
}

# Firebase Auth = Identity Platform. Email/password and anonymous (Kids) sign-in.
resource "google_identity_platform_config" "this" {
  project = var.project_id

  sign_in {
    allow_duplicate_emails = false

    email {
      enabled           = true
      password_required = true
    }

    anonymous {
      enabled = true
    }
  }

  depends_on = [google_firebase_project.this]
}

# Google sign-in needs an OAuth 2.0 web client. Ordinary (non-IAP) clients can't be created through Terraform or an
# API, so a maintainer creates it once and passes it in; see docs/INFRASTRUCTURE.md. Until it is supplied the
# provider is simply not enabled.
resource "google_identity_platform_default_supported_idp_config" "google" {
  count = var.google_oauth_client_id != "" && var.google_oauth_client_secret != "" ? 1 : 0

  project       = var.project_id
  idp_id        = "google.com"
  enabled       = true
  client_id     = var.google_oauth_client_id
  client_secret = var.google_oauth_client_secret

  depends_on = [google_identity_platform_config.this]
}

# Budget alert on the dev project (skipped when the billing account isn't provided).
data "google_project" "this" {
  project_id = var.project_id
}

resource "google_billing_budget" "dev" {
  count = var.billing_account != "" ? 1 : 0

  billing_account = var.billing_account
  display_name    = "Stay Focused ${var.env} budget"

  budget_filter {
    projects = ["projects/${data.google_project.this.number}"]
  }

  amount {
    specified_amount {
      currency_code = "EUR"
      units         = tostring(var.budget_amount)
    }
  }

  dynamic "threshold_rules" {
    for_each = [0.5, 0.9, 1.0]
    content {
      threshold_percent = threshold_rules.value
    }
  }

  depends_on = [google_project_service.base]
}
