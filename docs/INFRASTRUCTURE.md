# Infrastructure

## The rule

**Every resource is created and changed only through Terraform, applied by GitHub Actions.**
That covers GitHub (labels, milestones, backlog issues, repo settings, Pages) and GCP/Firebase
(projects, Firestore, Auth, rules, App Check, the 5 Cloud Functions, budgets). Nobody clicks in a console
to create or change infrastructure. A change is a PR: the Terraform plan is posted on the PR, CodeRabbit reviews the code,
and the apply runs when it merges to `main`. For an extra gate on applies, require reviewers on the GitHub environment
(`backlog`, `dev`, `prod`).

Terraform state lives in **HCP Terraform**. Workspaces use **Local execution mode**: HCP stores the state and locks,
and plan/apply run inside GitHub Actions.

Things Terraform can't do are handled, in order of preference, by:
1. A scripted, idempotent step in the same workflow (for example `scripts/backlog/sync_project.py` for the Projects v2 board,
   sub-issues and issue dependencies, which the GitHub provider doesn't support).
2. A `needs-human` story, for things with no API at all (Google Play Console, recruiting testers).

| Root module | Manages | Workflow | HCP workspace |
|---|---|---|---|
| `infra/github` | Labels, milestones, epics/stories from `backlog/`, repo settings (M1-12), Pages (M6-07) | `.github/workflows/backlog.yml` | `stayfocus-github` |
| `infra/bootstrap` | GCP projects, billing link, Workload Identity Federation, deploy service account (M1-13, M10-04) | `infra-bootstrap.yml` (manual) | `stayfocus-bootstrap` |
| `infra/firebase` | Firebase/GCP resources, added milestone by milestone (M1-13, M6-01, M7-01, M7-02, M8-05, M9-03, M10-04) | `infra-firebase.yml` | `stayfocus-firebase-dev`, `-prod` |

Infrastructure is built **phase by phase**: each milestone's `infra`-labelled stories add only the resources that milestone needs.

## One-time bootstrap (manual, done once by a maintainer)

These are the only manual steps, and they create credentials, not infrastructure:

1. **HCP Terraform:** create (or reuse) an organization. Set its **default execution mode to Local**, so that workspaces
   created by `terraform init` run in Actions. Create a team or org API token.
2. **GitHub repository settings → Secrets and variables → Actions:**
   - variable `TF_CLOUD_ORGANIZATION` = the HCP organization name
   - secret `TF_API_TOKEN` = the HCP API token
   No GitHub token secret is needed for issues, labels, milestones, sub-issues or dependencies: the workflow uses the
   built-in, short-lived `GITHUB_TOKEN` (`issues: write`).
3. **Credential for the kanban board** ([org project #11](https://github.com/orgs/dculussoftwares/projects/11), already created). Optional at first; the board step is skipped without it.
   Quick option: a fine-grained PAT (resource owner `dculussoftwares`, *Organization → Projects: Read and write* only, 1-year expiry) in the secret `BACKLOG_PROJECT_TOKEN`.
   Preferred option: `GITHUB_TOKEN` can't
   access org-level Projects v2, and this is a public repo, so use an App rather than a personal access token:
   create an org-owned GitHub App "stayfocus-automation" with *Organization → Projects: Read and write* and
   *Repository → Issues: Read and write, Metadata: Read*. Install it on `dculussoftwares/stayfocus` only. Then add:
   - variable `BACKLOG_APP_CLIENT_ID` = the App's client ID
   - secret `BACKLOG_APP_PRIVATE_KEY` = an App private key

   Story M1-12 adds *Repository → Administration* and *Pages* permissions to the same App, and switches the
   Terraform GitHub provider to it for repo settings.
4. **GitHub environment** `backlog` (Settings → Environments), with `main` as the only deployment branch. Optionally add
   required reviewers so applies need approval.
5. Run the **backlog** workflow (merge to `main`, or *Run workflow*). It creates the HCP workspace on first `init`,
   then labels, milestones, all epics and stories from `backlog/`, the sub-issue and "blocked by" links, and the
   kanban board #11 (if a board credential is set).

**Public repo hygiene:** no secrets, tokens, `google-services.json`, keystores or Terraform state in git (see `.gitignore`).
Fork PRs never get secrets (the plan job only runs for branches in this repo). Third-party actions are pinned to commit SHAs.

GCP bootstrap is described in the next section.

## Firebase/GCP

`infra/bootstrap` (manual, once) creates the project(s), billing link, a Workload Identity Federation (WIF) pool and
provider restricted to this repository, and the `tf-deploy` service account. `infra/firebase` (automatic) then manages
the resources inside the project, starting with the base APIs (`serviceusage`, `cloudresourcemanager`, `iam`, `firebase`).
GitHub Actions authenticates through WIF; **no service-account key** is used for deploys.

### Settings

| Where | Name | Value |
|---|---|---|
| variable | `TF_CLOUD_ORGANIZATION` | HCP organization (already set) |
| secret | `TF_API_TOKEN` | HCP token (already set) |
| variable | `GCP_WIF_PROVIDER_DEV` | bootstrap output `wif_provider`, copied exactly (`projects/<number>/locations/global/workloadIdentityPools/github/providers/github`) |
| variable | `GCP_DEPLOY_SA_DEV` | bootstrap output `deploy_service_acc` (`tf-deploy@stayfocus-dev.iam.gserviceaccount.com`) |
| variable | `GCP_PLAN_SA_DEV` | bootstrap output `plan_service_acc`: read-only (`serviceUsageViewer`) identity for PR plans |
| variable | `GCP_PROJECT_ID_DEV` | optional, default `stayfocus-dev` (forks: your own unique project id) |
| variable | `GCP_ORG_ID` | optional, GCP organization ID that owns the projects |
| secret | `GCP_BILLING_ACCOUNT` | billing account ID, bootstrap only |
| secret | `GCP_BOOTSTRAP_CREDENTIALS` | temporary key JSON of the bootstrap identity, **deleted after the first run** |
| environments | `bootstrap`, `dev` | `main` only; add required reviewers to gate applies |

HCP workspaces `stayfocus-bootstrap` and `stayfocus-firebase-dev` are created in Local execution mode by
`scripts/infra/ensure_tfc_workspace.sh`.

### Bootstrap steps (maintainer, once)

1. Create a throwaway bootstrap identity (a service account with *Project Creator* on the org/folder and *Billing
   Account User* on the billing account, plus permission to create WIF pools) and a key for it.
2. Add the secrets `GCP_BILLING_ACCOUNT` and `GCP_BOOTSTRAP_CREDENTIALS`, and the `bootstrap` environment.
3. Run **infra-bootstrap** from `main` with `apply` unchecked and read the plan, then run it again with `apply` checked.
4. Copy the printed `wif_provider` and `deploy_service_acc` into the variables above.
5. **Delete the key and the `GCP_BOOTSTRAP_CREDENTIALS` secret.** Check with
   `gcloud iam service-accounts keys list --iam-account=<bootstrap and tf-deploy accounts>`: only
   `SYSTEM_MANAGED` keys may remain.
6. Merge any change under `infra/firebase`: the `infra-firebase` workflow applies it. A later `terraform plan` on `main`
   shows no changes. The workflow only warns (and skips) until the variables exist.

Trust is bound to the numeric repository ID (names can be reused after a rename). Only workflows on `main` can
impersonate `tf-deploy`; pull-request plans use the read-only `tf-plan`. Changing `deploy_roles` later means re-running
infra-bootstrap with a fresh temporary key (steps 1-5), then deleting it again.

The deploy service account's roles (`deploy_roles` in `infra/bootstrap/variables.tf`) are least-privilege for what the
Firebase stories need; add roles there when a later infra story needs more.

### Forks

Run the same bootstrap from your fork with your own billing account (the workflow passes your repository ID), set
`GCP_PROJECT_ID_DEV` to a unique project id, then set the `GCP_*` variables to point at your own project. Set `TF_CLOUD_ORGANIZATION` to your own HCP organization.

## Editing the backlog

- Stories: `backlog/phase1/stories/Mxx-yy.md`. Epics: `backlog/phase1/epics/Mxx.md`.
  YAML front matter (`id`, `title`, `epic`, `size`, `modules`, `depends`, `labels`) + Markdown body.
- To add a story: add a file, give it a new id, list its `depends`, run `python3 scripts/backlog/waves.py`, and open a PR (check the plan comment).
- Don't edit issue titles or descriptions on GitHub; Terraform overwrites them. Progress goes on the kanban board and in issue comments.
  Assignees, state (open/closed) and board Status are not managed by Terraform.
- Board Status: the sync sets **Backlog** or **Ready** (Ready means every dependency is closed) for new items, promotes Backlog → Ready
  when dependencies close, and sets Done when an issue closes. Moving a card to *In progress* or *In review* is up to whoever works on it.

## Forks

Forks point `infra/*` at their own HCP organization, GitHub repo (`-var owner=... -var repository=...`) and GCP project.
The Android apps build without any of it (no `google-services.json` → Firebase features off).
