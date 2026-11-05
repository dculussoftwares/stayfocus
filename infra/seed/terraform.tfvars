# Public values. The billing account comes from TF_VAR_billing_account.
project_id           = "vesper-prod-31275"
org_id               = "42053967154"
github_repository_id = "1406642781"

# Immutable OIDC subject: owner and repository pinned by numeric ID, so a renamed or re-created repo can't match.
github_subject_prefix = "repo:dculussoftwares@98706461/vesper@1406642781"
