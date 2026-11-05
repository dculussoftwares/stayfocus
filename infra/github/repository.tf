# Repository settings, the branch ruleset for `main` and the `prod` environment.
#
# Needs a credential with Repository -> Administration: the apply job mints a GitHub App token for it
# (docs/INFRASTRUCTURE.md). Plans only read, so GITHUB_TOKEN is enough there.

import {
  to = github_repository.this
  id = var.repository
}

resource "github_repository" "this" {
  name         = var.repository
  description  = var.repository_description
  topics       = var.repository_topics
  visibility   = "public"
  has_issues   = true
  has_projects = true
  has_wiki     = true

  # Squash only, delete the branch on merge, allow auto-merge.
  allow_squash_merge     = true
  allow_merge_commit     = false
  allow_rebase_merge     = false
  allow_auto_merge       = true
  delete_branch_on_merge = true

  squash_merge_commit_title   = "PR_TITLE"
  squash_merge_commit_message = "PR_BODY"

  archive_on_destroy = true

  # Secret scanning and push protection (free on public repositories).
  security_and_analysis {
    secret_scanning {
      status = "enabled"
    }
    secret_scanning_push_protection {
      status = "enabled"
    }
  }

  lifecycle {
    prevent_destroy = true
  }
}

# GitHub Pages for /web, deployed by .github/workflows/pages.yml (actions/deploy-pages).
resource "github_repository_pages" "this" {
  repository = github_repository.this.name
  build_type = "workflow"
}

# Dependabot alerts.
resource "github_repository_vulnerability_alerts" "this" {
  repository = github_repository.this.name
  enabled    = true
}

resource "github_repository_ruleset" "main" {
  name        = "main"
  repository  = github_repository.this.name
  target      = "branch"
  enforcement = "active"

  conditions {
    ref_name {
      include = ["~DEFAULT_BRANCH"]
      exclude = []
    }
  }

  # Emergency escape hatch: an org admin may merge a PR without waiting for checks or approval.
  # Direct pushes to main stay rejected for everyone ("pull_request" bypass mode).
  bypass_actors {
    actor_id    = 0 # OrganizationAdmin (GitHub stores 0 for this actor type)
    actor_type  = "OrganizationAdmin"
    bypass_mode = "pull_request"
  }

  rules {
    deletion                = true
    non_fast_forward        = true
    required_linear_history = true

    pull_request {
      required_approving_review_count   = var.required_approvals
      dismiss_stale_reviews_on_push     = true
      required_review_thread_resolution = true
      require_code_owner_review         = false
      require_last_push_approval        = false
    }

    required_status_checks {
      strict_required_status_checks_policy = false

      dynamic "required_check" {
        for_each = var.required_status_checks
        content {
          context = required_check.value
        }
      }
    }
  }
}

# The environment infra applies run in. GCP only lets jobs in this environment use the deploy identity
# (infra/seed, wif_deploy), and the environment only accepts the main branch. No approval step: merging is the approval.
resource "github_repository_environment" "prod" {
  repository  = github_repository.this.name
  environment = "prod"

  deployment_branch_policy {
    protected_branches     = false
    custom_branch_policies = true
  }
}

resource "github_repository_environment_deployment_policy" "prod_main" {
  repository     = github_repository.this.name
  environment    = github_repository_environment.prod.environment
  branch_pattern = "main"
}
