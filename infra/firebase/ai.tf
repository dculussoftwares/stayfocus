# Firebase AI Logic (M9-03): the Gemini Developer API reached through the Firebase AI Logic proxy, with App Check
# enforced on the proxy, restricted Android API keys, a Gemini quota cap and a Gemini budget alert.

locals {
  ai_apis = [
    "firebasevertexai.googleapis.com",   # Firebase AI Logic API (the proxy the SDK calls)
    "firebaseml.googleapis.com",         # App Check service ID for Firebase AI Logic
    "generativelanguage.googleapis.com", # Gemini Developer API behind the proxy
    "apikeys.googleapis.com",
  ]

  # APIs the Android apps' keys may call: Firebase basics plus Firebase AI Logic.
  api_key_targets = [
    "firebasevertexai.googleapis.com",
    "firebaseappcheck.googleapis.com",
    "firebaseinstallations.googleapis.com",
    "identitytoolkit.googleapis.com",
    "securetoken.googleapis.com",
    "firestore.googleapis.com",
    "cloudfunctions.googleapis.com",
  ]
}

resource "google_project_service" "ai" {
  for_each = toset(local.ai_apis)

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false

  depends_on = [google_project_service.base]
}

# Only genuine app builds (Play Integrity, or a registered debug token) can call Gemini through the project.
# Same Play Integrity config and debug tokens as Firestore (appcheck.tf).
resource "google_firebase_app_check_service_config" "ai_logic" {
  provider = google-beta

  project          = var.project_id
  service_id       = "firebaseml.googleapis.com" # App Check's service ID for Firebase AI Logic
  enforcement_mode = var.app_check_enforcement

  depends_on = [
    google_project_service.ai,
    google_firebase_app_check_play_integrity_config.this,
  ]
}

# One key per app, limited to its package and signing certificates and to the APIs above. An app without SHA-1
# fingerprints gets no key (Android restrictions need at least one; an unrestricted key is not acceptable).
resource "google_apikeys_key" "android" {
  for_each = { for k, a in local.apps : k => a if length(var.sha1_fingerprints[k]) > 0 }

  project      = var.project_id
  name         = "${var.env}-${each.key}-android"
  display_name = "Stay Focused ${each.value.name} (${var.env}, Android)"

  restrictions {
    android_key_restrictions {
      dynamic "allowed_applications" {
        for_each = toset(var.sha1_fingerprints[each.key])
        content {
          package_name     = each.value.package
          sha1_fingerprint = allowed_applications.value
        }
      }
    }

    dynamic "api_targets" {
      for_each = toset(local.api_key_targets)
      content {
        service = api_targets.value
      }
    }
  }

  depends_on = [google_project_service.ai]
}

# Gemini request quota cap. Metric, unit and limit are inputs because the quota names depend on the model; empty skips.
resource "google_service_usage_consumer_quota_override" "gemini" {
  for_each = { for o in var.gemini_quota_overrides : "${o.metric}/${o.unit}" => o }

  provider       = google-beta
  project        = var.project_id
  service        = "generativelanguage.googleapis.com"
  metric         = urlencode(each.value.metric)
  limit          = urlencode(each.value.unit)
  override_value = tostring(each.value.limit)
  force          = true

  depends_on = [google_project_service.ai]
}

# Separate alert for Gemini spend (50/90/100%), on top of the project budget in main.tf.
resource "google_billing_budget" "gemini" {
  count = var.billing_account != "" ? 1 : 0

  billing_account = var.billing_account
  display_name    = "Stay Focused ${var.env} Gemini budget"

  budget_filter {
    projects = ["projects/${data.google_project.this.number}"]
    services = var.gemini_billing_services
  }

  amount {
    specified_amount {
      units = tostring(var.gemini_budget_amount)
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
