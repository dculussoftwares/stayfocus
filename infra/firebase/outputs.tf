output "env" {
  description = "Environment this root module manages."
  value       = var.env
}

output "enabled_apis" {
  description = "Base APIs enabled on the project."
  value       = sort([for s in google_project_service.base : s.service])
}
