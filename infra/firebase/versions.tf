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
      version = "~> 6.0"
    }
  }
}

# Credentials come from Workload Identity Federation (google-github-actions/auth); no key files.
provider "google" {
  project = var.project_id
  region  = var.region
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
}
