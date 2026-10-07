# Firebase web app for the GitHub Pages site (M6-07: /web account-deletion page).
# The web config is public by design (it identifies the project; security comes from Auth, rules and App Check).
# pages.yml reads the `firebase_web_config` output at deploy time and writes it to web/firebase-config.json.

resource "google_firebase_web_app" "site" {
  provider = google-beta

  project      = google_firebase_project.this.project
  display_name = "Stay Focused web"

  # Terraform removes the app on destroy instead of only abandoning it.
  deletion_policy = "DELETE"
}

data "google_firebase_web_app_config" "site" {
  provider = google-beta

  project    = var.project_id
  web_app_id = google_firebase_web_app.site.app_id
}
