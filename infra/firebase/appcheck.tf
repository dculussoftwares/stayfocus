# App Check for both Android apps (M7-01): Play Integrity in production builds, debug tokens for dev and CI, and
# enforcement on Firestore.

resource "google_firebase_app_check_play_integrity_config" "this" {
  provider = google-beta
  for_each = google_firebase_android_app.app

  project   = var.project_id
  app_id    = each.value.app_id
  token_ttl = "3600s"

  depends_on = [google_project_service.firestore]
}

locals {
  # "app/ci" style keys. Only the token values are secret, so the key list is made non-sensitive for for_each.
  app_check_debug_keys = nonsensitive(toset(flatten([
    for app, tokens in var.app_check_debug_tokens : [for name in keys(tokens) : "${app}/${name}"]
  ])))
}

resource "google_firebase_app_check_debug_token" "this" {
  provider = google-beta
  for_each = local.app_check_debug_keys

  project      = var.project_id
  app_id       = google_firebase_android_app.app[split("/", each.value)[0]].app_id
  display_name = "${var.env} ${each.value}"
  token        = var.app_check_debug_tokens[split("/", each.value)[0]][split("/", each.value)[1]]

  depends_on = [google_project_service.firestore]
}

resource "google_firebase_app_check_service_config" "firestore" {
  provider = google-beta

  project          = var.project_id
  service_id       = "firestore.googleapis.com"
  enforcement_mode = var.app_check_enforcement

  depends_on = [
    google_project_service.firestore,
    google_firestore_database.default,
    google_firebase_app_check_play_integrity_config.this,
  ]
}
