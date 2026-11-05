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

variable "github_subject_prefix" {
  description = "Start of the GitHub OIDC subject for this repository. The repository uses immutable subjects (owner and repo with their numeric IDs): gh api repos/OWNER/NAME/actions/oidc/customization/sub --jq .sub_claim_prefix"
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

variable "release_environment" {
  description = "GitHub environment whose jobs may use the Play upload identity. The environment only allows v* tags."
  type        = string
  default     = "release"
}
