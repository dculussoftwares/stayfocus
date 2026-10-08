terraform {
  required_version = ">= 1.9"

  # State lives in HCP Terraform (workspace stayfocus-firebase-dev, Local execution mode).
  # Organization and workspace come from TF_CLOUD_ORGANIZATION / TF_WORKSPACE (set in the workflow).
  cloud {}

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 8.5"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.7"
    }
  }
}

# Credentials come from Workload Identity Federation (google-github-actions/auth); no key files.
provider "google" {
  project = var.project_id
  region  = var.region

  # Billing budgets and Firebase Management are quota-billed to the project, not to the caller.
  user_project_override = true
  billing_project       = var.project_id
}

provider "google-beta" {
  project = var.project_id
  region  = var.region

  user_project_override = true
  billing_project       = var.project_id
}
