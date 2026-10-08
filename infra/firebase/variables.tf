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
  description = "Monthly budget (whole units of the billing account currency) that triggers alerts at 50%, 90% and 100%."
  type        = number
  default     = 20
}

variable "firestore_location" {
  description = "Firestore location (immutable after creation). A single region close to the users keeps latency and cost low."
  type        = string
  default     = "europe-west1"
}

variable "app_check_enforcement" {
  description = "App Check enforcement on Firestore: ENFORCED (requests without a valid token are rejected), UNENFORCED (metrics only, for a rollout period) or OFF."
  type        = string
  default     = "ENFORCED"

  validation {
    condition     = contains(["ENFORCED", "UNENFORCED", "OFF"], var.app_check_enforcement)
    error_message = "app_check_enforcement must be ENFORCED, UNENFORCED or OFF."
  }
}

variable "app_check_debug_tokens" {
  description = "App Check debug tokens (UUIDs) for dev and CI builds, per app and name, for example {\"app\":{\"ci\":\"<uuid>\"},\"kids\":{}}. Pass as TF_VAR_app_check_debug_tokens from a GitHub secret; never commit."
  type        = map(map(string))
  default     = {}
  sensitive   = true

  validation {
    condition     = alltrue([for app in keys(var.app_check_debug_tokens) : contains(["app", "kids"], app)])
    error_message = "app_check_debug_tokens keys must be app or kids."
  }
}
