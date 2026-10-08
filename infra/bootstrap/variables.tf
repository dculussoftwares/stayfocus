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
  description = "Project roles for the deploy service account. Least privilege: only what infra/firebase manages today (enabling APIs). Later infra stories add the roles they need (for example Firebase, Firestore, IAM) in their own PR."
  type        = list(string)
  default = [
    "roles/serviceusage.serviceUsageAdmin",
    # M6-01: Firebase project and Android apps (includes firebase.projects.update), and Auth/Identity Platform config.
    "roles/firebase.admin",
    "roles/identityplatform.admin",
    # Quota project for the billing budget API calls (user_project_override).
    "roles/serviceusage.serviceUsageConsumer",
    # M7-01: Firestore database, indexes and TTL (firebase.admin already covers rules and App Check).
    "roles/datastore.owner",
  ]
}

variable "plan_roles" {
  description = "Extra read-only project roles for the plan service account (PR plans refresh Firebase and Auth resources)."
  type        = list(string)
  default = [
    "roles/firebase.viewer",
    "roles/identityplatform.viewer",
    "roles/serviceusage.serviceUsageConsumer",
    "roles/datastore.viewer",
  ]
}
