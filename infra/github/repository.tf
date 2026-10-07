# Repository settings and the branch ruleset for `main` (M1-12).
#
# Needs a credential with Repository -> Administration: the apply job mints a GitHub App token for it
# (docs/INFRASTRUCTURE.md, bootstrap step 3). Plans only read, so GITHUB_TOKEN is enough there.

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

  lifecycle {
    prevent_destroy = true
  }
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
    actor_id    = 1 # OrganizationAdmin
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
