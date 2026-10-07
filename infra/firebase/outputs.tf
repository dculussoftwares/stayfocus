output "env" {
  description = "Environment this root module manages."
  value       = var.env
}

output "firebase_app_ids" {
  description = "Firebase app IDs per app (app, kids)."
  value       = { for k, a in google_firebase_android_app.app : k => a.app_id }
}

output "google_services_json" {
  description = "google-services.json contents per app (app, kids). Write into app/ and kids/ for Firebase-enabled builds; never commit. Read with: terraform output -json google_services_json."
  value       = { for k, c in data.google_firebase_android_app_config.this : k => c.config_file_contents }
  sensitive   = true
}

output "enabled_apis" {
  description = "Base APIs enabled on the project."
  value       = sort([for s in google_project_service.base : s.service])
}
