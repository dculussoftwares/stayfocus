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
    functionsRegion   = var.region
  }
}

output "firebase_app_ids" {
  description = "Firebase app IDs per app (app, kids)."
  value       = { for k, a in google_firebase_android_app.app : k => a.app_id }
}

output "google_services_json" {
  description = "google-services.json contents per app (app, kids). Write into app/ and kids/ for Firebase-enabled builds; never commit. Read with: terraform output -json google_services_json."
  value       = { for k, c in data.google_firebase_android_app_config.this : k => base64decode(c.config_file_contents) }
  sensitive   = true
}

output "enabled_apis" {
  description = "Base APIs enabled on the project."
  value       = sort([for s in google_project_service.base : s.service])
}
