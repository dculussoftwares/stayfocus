variable "project_id" {
  description = "The one GCP project that holds everything (Firebase, state bucket, CI identities)."
  type        = string
}

variable "org_id" {
  description = "GCP organization that owns the project."
  type        = string
}

variable "billing_account" {
  description = "Billing account ID (XXXXXX-XXXXXX). Passed as TF_VAR_billing_account (GitHub secret GCP_BILLING_ACCOUNT)."
  type        = string
}

variable "github_repository" {
  description = "owner/name of the repository whose workflows may log in to GCP."
  type        = string
}

variable "github_repository_id" {
  description = "Numeric ID of that repository (gh api repos/OWNER/NAME --jq .id). Immutable, unlike the name."
  type        = string
}

variable "deploy_environment" {
  description = "GitHub environment whose jobs may use the deploy identity. The environment only allows the main branch."
  type        = string
  default     = "prod"
}

variable "region" {
  description = "Default region (state bucket, Functions)."
  type        = string
  default     = "europe-west1"
}
