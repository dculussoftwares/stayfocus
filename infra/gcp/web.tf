# Firebase web app for the website (web/, vesper.dculus.com; the account-deletion page).
# The web config is public by design (it identifies the project; security comes from Auth, rules and App Check).
# web.yml reads the `firebase_web_config` output at deploy time and writes it to web/public/firebase-config.json.

resource "google_firebase_web_app" "site" {
  provider = google-beta

  project      = google_firebase_project.this.project
  display_name = "Vesper web"

  # Terraform removes the app on destroy instead of only abandoning it.
  deletion_policy = "DELETE"
}

data "google_firebase_web_app_config" "site" {
  provider = google-beta

  project    = var.project_id
  web_app_id = google_firebase_web_app.site.app_id
}

# App Check for the website. The callables (deleteAccount) enforce App Check, so the web page needs a token: a score
# (invisible) reCAPTCHA Enterprise key limited to the website's domain. The site key is public and goes into
# firebase_web_config.
resource "google_project_service" "recaptcha_enterprise" {
  project            = var.project_id
  service            = "recaptchaenterprise.googleapis.com"
  disable_on_destroy = false

  depends_on = [google_project_service.base]
}

resource "google_recaptcha_enterprise_key" "web" {
  project      = var.project_id
  display_name = "Vesper web (App Check)"

  web_settings {
    integration_type  = "SCORE"
    allowed_domains   = [var.web_hostname]
    allow_amp_traffic = false
  }

  depends_on = [google_project_service.recaptcha_enterprise]
}

resource "google_firebase_app_check_recaptcha_enterprise_config" "web" {
  provider = google-beta

  project   = var.project_id
  app_id    = google_firebase_web_app.site.app_id
  site_key  = google_recaptcha_enterprise_key.web.name
  token_ttl = "3600s"
}
