variable "environments" {
  description = "Environments to create. Prod is added by M10-04."
  type = map(object({
    project_id = string
  }))
  default = {
    dev = { project_id = "stayfocus-dev" }
  }
}

variable "billing_account" {
  description = "Billing account ID (XXXXXX-XXXXXX) to link to the projects."
  type        = string
}

variable "org_id" {
  description = "Optional GCP organization ID that owns the projects. Empty creates them without a parent."
  type        = string
  default     = ""
}

variable "github_repository_id" {
  description = "Numeric GitHub repository ID allowed to use Workload Identity Federation (gh api repos/OWNER/NAME --jq .id). Immutable, unlike the name."
  type        = string
}

variable "deploy_branch" {
  description = "Branch whose workflows may impersonate the deploy service account."
  type        = string
  default     = "main"
}

variable "region" {
  description = "Default GCP region."
  type        = string
  default     = "europe-west1"
}

variable "deploy_roles" {
  description = "Project roles for the deploy service account. Includes Firebase project management for infra/firebase, plus API management."
  type        = list(string)
  default = [
    "roles/serviceusage.serviceUsageAdmin",
    "roles/firebase.admin",
  ]
}
