terraform {
  required_version = ">= 1.9"

  # State in HCP Terraform (workspace stayfocus-bootstrap, Local execution mode).
  cloud {}

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}

# One-time run: credentials come from the temporary GOOGLE_CREDENTIALS bootstrap secret
# (see docs/INFRASTRUCTURE.md). Delete the secret and its key after the first apply.
provider "google" {
  region = var.region
}
