terraform {
  required_version = ">= 1.9"

  # State lives in HCP Terraform. Organization and workspace come from the
  # TF_CLOUD_ORGANIZATION and TF_WORKSPACE environment variables (set in the
  # GitHub Actions workflow). Workspaces use the "Local" execution mode, so
  # plans and applies run inside GitHub Actions.
  cloud {}

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
