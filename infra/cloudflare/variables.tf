variable "account_id" {
  description = "Cloudflare account ID (public value)."
  type        = string
}

variable "zone_id" {
  description = "ID of the existing dculus.com zone. Read only: this module never creates or changes the zone."
  type        = string
}

variable "zone_name" {
  description = "Name of the existing zone."
  type        = string
}

variable "web_hostname" {
  description = "Hostname the website is served on."
  type        = string
}

variable "web_worker_name" {
  description = "Name of the Worker that serves the website. Must match `name` in web/wrangler.jsonc."
  type        = string
}
