# Infrastructure

## The rule

Every resource is created and changed through Terraform, applied by GitHub Actions. That covers GitHub (repo settings,
ruleset, environments, backlog labels/milestones/issues, Pages) and GCP/Firebase. A change is a PR: the plan is posted on
the PR and the apply runs when it merges to `main`. Nobody creates infrastructure in a console.

There is **one GCP project** (`vesper-prod-31275`, org `natheeshece-org`). Debug builds and release builds use it.

| Module | Manages | State (GCS) |
|---|---|---|
| `infra/seed` | The project, billing link, Terraform state bucket, keyless GitHub login (Workload Identity Federation), the `tf-deploy` and `tf-plan` identities | `seed/` |
| `infra/gcp` | Firebase project and apps, Auth, Firestore + rules, App Check, Cloud Functions, AI Logic, monitoring, budgets | `gcp/` |
| `infra/github` | Repo settings, `main` ruleset, `prod` environment, Pages, backlog labels/milestones/issues | `github/` |

One workflow runs all three: `.github/workflows/infra.yml`.

- **PR:** `validate` (every module, no credentials, forks too), then `plan` (every module, read-only identity, one PR comment per module).
- **Merge to `main`:** `apply-seed`, then `apply-gcp` and `apply-github`, in the GitHub environment `prod`. No approval step: merging is the approval.

`backlog-board.yml` updates the kanban board when an issue closes or reopens.

## How CI logs in to GCP (no keys)

`google-github-actions/auth` exchanges the job's GitHub OIDC token for a short-lived GCP token (the GCP equivalent of
`aws-actions/configure-aws-credentials` or `azure/login`). No service-account key exists anywhere.

| Identity | Who can use it | Rights |
|---|---|---|
| `tf-deploy` | Only jobs of this repository running in the GitHub environment `prod`, which only accepts `main`. Matched on the immutable OIDC subject `repo:<owner>@<owner id>/<repo>@<repo id>:environment:prod` | Owner of the project; budgets on the billing account |
| `tf-plan` | Any job of this repository (fork PRs get no OIDC token) | Read-only: viewer roles, Terraform state |

Because `tf-deploy` owns the project, adding a resource (and the roles it needs) is one PR; no person has to grant
anything. The values CI needs to log in are public and live in `infra/prod.env` (output `prod_env` of `infra/seed`).
The shared setup (load `prod.env`, log in, `terraform init` against the bucket) is `.github/actions/terraform-init`.

## Labels (cost allocation)

Every resource that supports labels gets `app=vesper`, `env=prod`, `managed-by=terraform` and `component`
(`foundation` for the seed, `app` for `infra/gcp`), set once as the provider's `default_labels`. The project carries
the same labels, so every cost rolls up under them in billing reports. IAM bindings and identities have no labels (and no cost).

## Secrets and variables

Public configuration is committed: `infra/prod.env`, `infra/seed/terraform.tfvars`, `infra/gcp/terraform.tfvars`
(fingerprints, App Check enforcement, budgets, Gemini quotas). Only secrets live in GitHub:

| Kind | Name | Used for |
|---|---|---|
| secret | `GCP_BILLING_ACCOUNT` | Billing link (seed) and budgets (gcp) |
| secret | `BACKLOG_APP_PRIVATE_KEY` + variable `BACKLOG_APP_CLIENT_ID` | GitHub App token: repo settings, ruleset, project board |
| variable `GOOGLE_OAUTH_WEB_CLIENT_ID` + secret `GOOGLE_OAUTH_WEB_CLIENT_SECRET` | optional | Google sign-in (see below). Empty = provider off |
| secret | `APP_CHECK_DEBUG_TOKENS` | optional: `{"app":{"<name>":"<uuid>"},"kids":{}}` debug tokens registered with App Check |
| secret | `ALERT_EMAIL` | optional: where Cloud Monitoring alerts go |

## First run (once)

Everything after this runs in Actions. Run it from a laptop or Google Cloud Shell, logged in as an org admin with
billing access (`gcloud auth login`); no key is created.

```bash
cd infra/seed
printf 'terraform {\n  backend "local" {}\n}\n' > backend_override.tf      # the bucket doesn't exist yet
export GOOGLE_OAUTH_ACCESS_TOKEN=$(gcloud auth print-access-token) TF_VAR_billing_account=<billing account>
terraform init && terraform apply
rm backend_override.tf
terraform init -migrate-state -force-copy -backend-config=bucket=<project_id>-tfstate   # move the state into the bucket
rm -f terraform.tfstate*
terraform output -raw prod_env > ../prod.env                                # commit this file
```

Then merge: Actions creates everything else. Manual steps that have no API:

1. **GitHub App** (org-owned, installed on this repo only): *Repository → Administration, Issues, Pages: read and write*,
   *Metadata: read*, *Organization → Projects: read and write*. Set `BACKLOG_APP_CLIENT_ID` and `BACKLOG_APP_PRIVATE_KEY`.
2. **Google sign-in:** ordinary OAuth clients have no create API, so create a *Web application* client once in
   *Google Auth Platform → Clients* of the project, then set `GOOGLE_OAUTH_WEB_CLIENT_ID` (variable) and
   `GOOGLE_OAUTH_WEB_CLIENT_SECRET` (secret). The web client ID is also what Credential Manager uses in the app.
3. **Play Console**, once the apps exist: link Play Integrity to both apps, and add the *Play App Signing* SHA-1/SHA-256
   fingerprints to `infra/gcp/terraform.tfvars`.

If CI ever loses access, a project owner can always fix it from Cloud Shell; nothing depends on a stored credential.

## Working with it

- **Add a resource:** edit `infra/gcp` (one file per area), open a PR, read the plan comment, merge.
- **Firestore rules:** edit `firebase/firestore.rules`; the plan shows a ruleset replacement, the merge releases it. Never `firebase deploy`.
- **Cloud Functions:** `infra/gcp/functions.tf`. The workflow builds `firebase/functions` (one folder per function in
  `dist/`); Terraform zips each one under a content hash, so only changed functions redeploy. Each function has its own
  runtime service account with the roles listed in `local.functions`.
- **Firebase config for builds:** `scripts/ci/fetch-google-services.sh` writes `app/` and `kids/google-services.json`
  from the `google_services_json` output (read-only identity). CI uses it on `main`, `release.yml` for releases, and
  `pages.yml` reads `firebase_web_config` the same way. Locally: `gcloud auth login`, then
  `TF_STATE_BUCKET=<bucket> scripts/ci/fetch-google-services.sh`. The files are git-ignored; never commit them.
- **Run Terraform locally (read-only):** `GOOGLE_OAUTH_ACCESS_TOKEN=$(gcloud auth print-access-token) terraform init -backend-config=bucket=<bucket> && terraform plan -lock=false`.
  Don't apply locally.

## Public repo hygiene

No secrets, tokens, `google-services.json`, keystores or Terraform state in git (`.gitignore`, the `forbidden-files`
pre-commit hook). Fork PRs get no secrets and no OIDC token, so they only validate. Plans are posted on the PR and in the
job log, which are public: keep every secret variable `sensitive = true`. Third-party actions are pinned to commit SHAs.

## Editing the backlog

- Stories: `backlog/phase1/stories/Mxx-yy.md`. Epics: `backlog/phase1/epics/Mxx.md`.
  YAML front matter (`id`, `title`, `epic`, `size`, `modules`, `depends`, `labels`) + Markdown body.
- To add a story: add a file, give it a new id, list its `depends`, run `python3 scripts/backlog/waves.py`, and open a PR (check the plan comment).
- Don't edit issue titles or descriptions on GitHub; Terraform overwrites them. Progress goes on the kanban board and in issue comments.
  Assignees, state (open/closed) and board Status are not managed by Terraform.
- Board Status: the sync sets **Backlog** or **Ready** (Ready means every dependency is closed) for new items, promotes Backlog → Ready
  when dependencies close, and sets Done when an issue closes. Moving a card to *In progress* or *In review* is up to whoever works on it.


## Repository settings and branch ruleset

`infra/github/repository.tf` manages the repository (squash-only merges, delete branch on merge, auto-merge allowed;
imported with an `import` block) and a ruleset on the default branch:

- pull request required, stale approvals dismissed on push, **all review threads resolved**;
- required status checks: `ci-pass` (aggregate job in `ci.yml`), `security-pass` (aggregate job in `security.yml`) and `gradle-dependency-graph` (`dependency-graph.yml`);
- linear history;
- org admins bypass it (`always`): they can push straight to `main`; everyone else needs a PR;
- a second ruleset, `main-protect`, blocks force pushes and deleting `main` for everyone, with no bypass.

**Review mechanism: `required_approvals = 0`.** Qodo, the fallback reviewer, cannot approve, so a required approval
would block fallback merges. The `CodeRabbit` check is not required either (it does not exist in fallback mode). The
CodeRabbit/Qodo gate is enforced by the agent rules in `docs/agents/PHASE1_EXECUTION.md`; GitHub enforces `ci-pass` and
resolved threads. Don't raise `required_approvals` without revisiting the fallback.


## Security scanning

- `.github/workflows/security.yml` (aggregate `security-pass`, required): dependency review (fails on high severity and on
  licences incompatible with GPL-3.0), gitleaks (PR commits; full history on `main` and weekly), Trivy config scan of `infra/`
  (SARIF to the Security tab, fails on high/critical; exceptions in `.trivyignore` with a reason), tflint with the google ruleset
  (`.tflint.hcl`, also used by the pre-commit hook), zizmor on the workflows (high; exceptions in `.github/zizmor.yml`).
- Gradle is not in GitHub's dependency graph by itself. `dependency-graph.yml` generates it (PRs, forks included, with a read-only
  token and an uploaded artifact; `main` submits directly) and `dependency-graph-submit.yml` (`workflow_run`, runs no PR code)
  submits the PR's graph while dependency review waits and retries. The dependency graph itself is turned on by the Dependabot
  alerts setting, so the first PR after the first apply is the first one these jobs can pass.
- `.github/workflows/codeql.yml`: CodeQL for `java-kotlin`, `javascript-typescript` and `actions` (PRs, `main`, weekly); alerts are
  reviewed in the Security tab and are not a merge gate. A language without sources yet is skipped.
- `.github/dependabot.yml`: Gradle, npm (`firebase/functions`), GitHub Actions, Terraform and pre-commit, weekly, minor/patch grouped.
- `github_repository` turns on secret scanning, push protection and Dependabot alerts (needs the GitHub App permissions).


## Forks

Run the first run above with your own org, billing account and project ID (edit `infra/seed/terraform.tfvars`, including
`github_repository_id` and `github_subject_prefix`), commit your own `infra/prod.env`, and set `project_id` in
`infra/gcp/terraform.tfvars`. The Android apps build without any of it (no `google-services.json` → Firebase features off).
