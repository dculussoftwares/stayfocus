output "web_url" {
  description = "URL of the website."
  value       = "https://${cloudflare_workers_custom_domain.web.hostname}"
}
