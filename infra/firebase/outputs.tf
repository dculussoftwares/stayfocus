output "env" {
  description = "Environment this root module manages."
  value       = var.env
}

output "firebase_web_config" {
  description = "Public Firebase web config for the Pages site (written to web/firebase-config.json by pages.yml)."
  value = {
    apiKey            = data.google_firebase_web_app_config.site.api_key
    authDomain        = data.google_firebase_web_app_config.site.auth_domain
    projectId         = var.project_id
    storageBucket     = data.google_firebase_web_app_config.site.storage_bucket
    messagingSenderId = data.google_firebase_web_app_config.site.messaging_sender_id
    appId             = google_firebase_web_app.site.app_id
    appCheckSiteKey   = var.app_check_site_key
    functionsRegion   = var.region
  }
}

output "enabled_apis" {
  description = "Base APIs enabled on the project."
  value       = sort([for s in google_project_service.base : s.service])
}
