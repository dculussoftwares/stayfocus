# Release tags may only be created by explicitly provisioned release managers.
# This prevents an arbitrary tag from selecting unreviewed workflow code.
resource "github_repository_ruleset" "release_tags" {
  name        = "release-tags"
  repository  = var.repository
  target      = "tag"
  enforcement = "active"

  conditions {
    ref_name {
      include = ["refs/tags/v*"]
      exclude = []
    }
  }

  rules {
    creation                = true
    update                  = true
    deletion                = true
    non_fast_forward        = true
  }

  dynamic "bypass_actors" {
    for_each = var.release_manager_ids
    content {
      actor_id    = bypass_actors.value
      actor_type  = "User"
      bypass_mode = "always"
    }
  }
}

# Environment reviewers must approve before signing and Play secrets become available.
resource "github_repository_environment" "release" {
  repository  = var.repository
  environment = "release"

  reviewers {
    users = tolist(var.release_manager_ids)
  }
}
