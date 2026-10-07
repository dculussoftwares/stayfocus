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

variable "sha1_fingerprints" {
  description = "SHA-1 certificate fingerprints (debug and release) per Android app (app, kids). Public values, not secrets."
  type        = map(list(string))
  default = {
    app  = []
    kids = []
  }
}

variable "sha256_fingerprints" {
  description = "SHA-256 certificate fingerprints (debug and release) per Android app (app, kids). Public values, not secrets."
  type        = map(list(string))
  default = {
    app  = []
    kids = []
  }
}

variable "google_oauth_client_id" {
  description = "OAuth 2.0 web client ID for Google sign-in (created once by a maintainer; see docs/INFRASTRUCTURE.md). Empty leaves the Google provider disabled."
  type        = string
  default     = ""
}

variable "google_oauth_client_secret" {
  description = "Secret of the OAuth 2.0 web client. Pass it as TF_VAR_google_oauth_client_secret from a GitHub secret."
  type        = string
  default     = ""
  sensitive   = true
}

variable "billing_account" {
  description = "Billing account ID for the budget alert. Empty skips the budget."
  type        = string
  default     = ""
}

variable "budget_amount" {
  description = "Monthly budget (whole EUR) that triggers alerts at 50%, 90% and 100%."
  type        = number
  default     = 20
}
