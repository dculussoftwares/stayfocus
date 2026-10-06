# Phase 1 backlog as code.
#
# Each file in backlog/phase1/{epics,stories} is one issue: YAML front matter
# (id, title, epic, size, modules, depends, labels) followed by the Markdown body.
# Edit the files in a PR; the backlog workflow plans on the PR and applies on merge.
#
# Not covered by the GitHub provider, so scripts/backlog/sync_project.py
# handles them after apply: the Projects v2 board, sub-issues (epic -> story)
# and "blocked by" dependencies.

locals {
  front_matter_re = "(?s)^---\\n(.*?)\\n---\\n\\n?(.*)$"

  epics = {
    for f in fileset("${path.module}/${var.backlog_dir}/epics", "*.md") :
    yamldecode(regex(local.front_matter_re, file("${path.module}/${var.backlog_dir}/epics/${f}"))[0]).id => {
      meta = yamldecode(regex(local.front_matter_re, file("${path.module}/${var.backlog_dir}/epics/${f}"))[0])
      body = regex(local.front_matter_re, file("${path.module}/${var.backlog_dir}/epics/${f}"))[1]
      file = "backlog/phase1/epics/${f}"
    }
  }

  stories = {
    for f in fileset("${path.module}/${var.backlog_dir}/stories", "*.md") :
    yamldecode(regex(local.front_matter_re, file("${path.module}/${var.backlog_dir}/stories/${f}"))[0]).id => {
      meta = yamldecode(regex(local.front_matter_re, file("${path.module}/${var.backlog_dir}/stories/${f}"))[0])
      body = regex(local.front_matter_re, file("${path.module}/${var.backlog_dir}/stories/${f}"))[1]
      file = "backlog/phase1/stories/${f}"
    }
  }

  stories_by_epic = {
    for epic_id in keys(local.epics) :
    epic_id => sort([for id, s in local.stories : id if s.meta.epic == epic_id])
  }

  managed_note = "_This issue is managed by Terraform. Edit `%s` in a PR instead of changing this description; manual edits are overwritten on the next apply. Track progress on the kanban board and in comments._"

  label_defs = {
    "type:epic"   = { color = "5319e7", description = "Milestone-level epic; stories are its sub-issues" }
    "type:story"  = { color = "1d76db", description = "One PR-sized unit of work an agent can finish" }
    "size:S"      = { color = "c2e0c6", description = "Up to half a day" }
    "size:M"      = { color = "fef2c0", description = "About 1-2 days" }
    "size:L"      = { color = "f9d0c4", description = "About 3-5 days; split if it grows" }
    "infra"       = { color = "0e8a16", description = "Terraform / GitHub Actions; no manual console changes" }
    "needs-human" = { color = "b60205", description = "Needs a person (Play Console, testers, accounts)" }
    "agent-ready" = { color = "c6f432", description = "Fully specified; any coding agent can pick it up" }
  }
}

resource "github_issue_label" "this" {
  for_each = local.label_defs

  repository  = var.repository
  name        = each.key
  color       = each.value.color
  description = each.value.description
}

resource "github_repository_milestone" "this" {
  for_each = local.epics

  owner       = var.owner
  repository  = var.repository
  title       = "${each.key} · ${each.value.meta.title}"
  description = "Phase 1, ${each.key}: ${each.value.meta.title}. See docs/IMPLEMENTATION_PLAN.md."
  state       = "open"
}

resource "github_issue" "epic" {
  for_each = local.epics

  repository       = var.repository
  title            = "[${each.key}] Epic: ${each.value.meta.title}"
  milestone_number = github_repository_milestone.this[each.key].number
  labels           = [github_issue_label.this["type:epic"].name]

  body = join("\n", concat(
    [trimspace(each.value.body), "", "## Stories", ""],
    [for id in local.stories_by_epic[each.key] : "- **${id}** ${local.stories[id].meta.title}"],
    ["", "Stories are linked as sub-issues of this epic. Order and dependencies are in each story.", "", "---", format(local.managed_note, each.value.file)],
  ))

  lifecycle {
    ignore_changes = [assignees]
  }
}

resource "github_issue" "story" {
  for_each = local.stories

  repository       = var.repository
  title            = "[${each.key}] ${each.value.meta.title}"
  milestone_number = github_repository_milestone.this[each.value.meta.epic].number
  labels = concat(
    [github_issue_label.this["type:story"].name, "size:${each.value.meta.size}"],
    contains(each.value.meta.labels, "needs-human") ? [] : ["agent-ready"],
    each.value.meta.labels,
  )

  body = join("\n", [
    "> **Epic:** #${github_issue.epic[each.value.meta.epic].number} · **Size:** ${each.value.meta.size} · **Modules:** ${join(", ", [for m in each.value.meta.modules : "`${m}`"])}",
    length(each.value.meta.depends) == 0
    ? "> **Depends on:** nothing. Ready to start."
    : "> **Depends on:** ${join(", ", [for d in each.value.meta.depends : "**${d}** ${local.stories[d].meta.title}"])} (linked as \"blocked by\")",
    "",
    "**Before you start:** read `AGENTS.md`, `docs/IMPLEMENTATION_PLAN.md` (workflow §4, Definition of done) and the design handoff in `design_handoff_stay_focused_phase1/`. Workflow: Prompt B in `docs/agents/PHASE1_EXECUTION.md`. Move this card to *In progress* when you start and *In review* when your PR is open. The PR says `Closes #<this issue>` and merges only after CodeRabbit approves and CI is green.",
    "",
    trimspace(each.value.body),
    "",
    "---",
    format(local.managed_note, each.value.file),
  ])

  depends_on = [github_issue_label.this]

  lifecycle {
    ignore_changes = [assignees]
  }
}
