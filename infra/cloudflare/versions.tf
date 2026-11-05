terraform {
  required_version = ">= 1.9"

  # State in the bucket created by infra/seed (terraform init -backend-config=bucket=$TF_STATE_BUCKET).
  backend "gcs" {
    prefix = "cloudflare"
  }

  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.28"
    }
  }
}

provider "cloudflare" {
  # Token is read from the CLOUDFLARE_API_TOKEN environment variable (GitHub secrets, see docs/INFRASTRUCTURE.md).
}
