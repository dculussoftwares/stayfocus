# Repository settings, the branch ruleset for `main` and the `prod` and `release` environments.
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

  # Org admins may push straight to main (and merge PRs without waiting for checks). Everyone else needs a PR.
  # Force pushes and deleting main stay blocked for everyone (ruleset main_protect, no bypass).
  bypass_actors {
    actor_id    = 0 # OrganizationAdmin (GitHub stores 0 for this actor type)
    actor_type  = "OrganizationAdmin"
    bypass_mode = "always"
  }

  rules {
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

# No force pushes and no deletion of main, for everyone (no bypass).
resource "github_repository_ruleset" "main_protect" {
  name        = "main-protect"
  repository  = github_repository.this.name
  target      = "branch"
  enforcement = "active"

  conditions {
    ref_name {
      include = ["~DEFAULT_BRANCH"]
      exclude = []
    }
  }

  rules {
    deletion         = true
    non_fast_forward = true
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

# The environment the release workflow (.github/workflows/release.yml) runs in. It holds the signing and Play secrets
# (UPLOAD_KEYSTORE_BASE64, UPLOAD_KEYSTORE_PASSWORD, UPLOAD_KEY_ALIAS, UPLOAD_KEY_PASSWORD, PLAY_SERVICE_ACCOUNT_JSON).
# Those values are added by hand in GitHub, never through Terraform, so they never reach state. Only `v*` tags may
# deploy here, and a reviewer approves every run before a signed build is made.
data "github_user" "release_reviewer" {
  for_each = toset(var.release_reviewers)
  username = each.value
}

resource "github_repository_environment" "release" {
  repository  = github_repository.this.name
  environment = "release"

  # One maintainer may tag and approve their own release.
  prevent_self_review = false

  reviewers {
    users = [for u in data.github_user.release_reviewer : tonumber(u.id)]
  }

  deployment_branch_policy {
    protected_branches     = false
    custom_branch_policies = true
  }
}

resource "github_repository_environment_deployment_policy" "release_tags" {
  repository  = github_repository.this.name
  environment = github_repository_environment.release.environment
  tag_pattern = "v*"
}
