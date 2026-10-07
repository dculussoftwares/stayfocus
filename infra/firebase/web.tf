# Firebase web app for the GitHub Pages site (M6-07: /web account-deletion page).
# The web config is public by design (it identifies the project; security comes from Auth, rules and App Check).
# pages.yml reads the `firebase_web_config` output at deploy time and writes it to web/firebase-config.json.
#
# NOTE: M6-01 declares the same Firebase API and google_firebase_project under other addresses. Enabling an API and
# adding Firebase to a project are idempotent, so the two coexist; dedupe them in a follow-up once both have landed.

resource "google_project_service" "firebase_web" {
  project = var.project_id
  service = "firebase.googleapis.com"

  disable_on_destroy = false
}

resource "google_firebase_project" "web" {
  provider = google-beta
  project  = var.project_id

  depends_on = [google_project_service.firebase_web]
}

resource "google_firebase_web_app" "site" {
  provider = google-beta

  project      = google_firebase_project.web.project
  display_name = "Stay Focused web"

  # Terraform removes the app on destroy instead of only abandoning it.
  deletion_policy = "DELETE"
}

resource "google_firebase_app_check_recaptcha_enterprise_config" "web" {
  count    = var.app_check_site_key == "" ? 0 : 1
  provider = google-beta

  project  = var.project_id
  app_id   = google_firebase_web_app.site.app_id
  site_key = var.app_check_site_key
}

# Keep the production Pages origin in Firebase Authentication's allowlist so Google sign-in
# works without a console-only post-deploy step. The remaining Auth settings are owned by M6-01.
resource "google_identity_platform_config" "auth" {
  project = var.project_id

  authorized_domains = [
    "localhost",
    "${var.project_id}.firebaseapp.com",
    "${var.project_id}.web.app",
    "dculussoftwares.github.io",
  ]

  depends_on = [google_firebase_project.web]
}

data "google_firebase_web_app_config" "site" {
  provider = google-beta

  project    = var.project_id
  web_app_id = google_firebase_web_app.site.app_id
}
