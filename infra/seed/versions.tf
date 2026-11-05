terraform {
  required_version = ">= 1.9"

  # State lives in the bucket this module creates. The bucket name comes from infra/prod.env
  # (terraform init -backend-config=bucket=$TF_STATE_BUCKET). See docs/INFRASTRUCTURE.md for the very first run.
  backend "gcs" {
    prefix = "seed"
  }

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}

provider "google" {
  region = var.region

  # GCP's cost-allocation tags: every resource that supports labels gets these, and they show up in billing reports.
  default_labels = {
    app        = "vesper"
    env        = "prod"
    managed-by = "terraform"
    component  = "foundation"
  }
}
