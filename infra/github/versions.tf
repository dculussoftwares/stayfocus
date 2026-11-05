terraform {
  required_version = ">= 1.9"

  # State in the bucket created by infra/seed (terraform init -backend-config=bucket=$TF_STATE_BUCKET).
  backend "gcs" {
    prefix = "github"
  }

  required_providers {
    github = {
      source  = "integrations/github"
      version = "~> 6.6"
    }
  }
}

provider "github" {
  owner = var.owner
  # Token is read from the GITHUB_TOKEN environment variable.
}
