terraform {
  required_version = ">= 1.9"

  # State in the bucket created by infra/seed (terraform init -backend-config=bucket=$TF_STATE_BUCKET).
  backend "gcs" {
    prefix = "gcp"
  }

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 6.0"
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

  # Cost-allocation labels on every resource that supports them (shown in billing reports).
  default_labels = local.labels
}

provider "google-beta" {
  project = var.project_id
  region  = var.region

  user_project_override = true
  billing_project       = var.project_id

  default_labels = local.labels
}

locals {
  labels = {
    app        = "vesper"
    env        = "prod"
    managed-by = "terraform"
    component  = "app"
  }
}
