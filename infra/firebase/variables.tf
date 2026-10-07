variable "project_id" {
  description = "GCP project that hosts this environment's Firebase resources (for example stayfocus-dev)."
  type        = string
}

variable "region" {
  description = "Default GCP region."
  type        = string
  default     = "europe-west1"
}

variable "env" {
  description = "Environment name: dev or prod."
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "prod"], var.env)
    error_message = "env must be dev or prod."
  }
}

variable "app_check_site_key" {
  description = "reCAPTCHA Enterprise site key used by Firebase App Check for the Pages web app."
  type        = string
  default     = ""
}
