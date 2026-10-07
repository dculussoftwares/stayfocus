variable "owner" {
  description = "GitHub organization that owns the repository."
  type        = string
  default     = "dculussoftwares"
}

variable "repository" {
  description = "Repository the backlog issues are created in."
  type        = string
  default     = "stayfocus"
}

variable "backlog_dir" {
  description = "Backlog directory, relative to this module."
  type        = string
  default     = "../../backlog/phase1"
}

variable "repository_description" {
  description = "Repository description."
  type        = string
  default     = "Free, open-source Android app blocker with a Kids companion app for parent-to-child control."
}

variable "repository_topics" {
  description = "Repository topics."
  type        = list(string)
  default     = ["android", "kotlin", "jetpack-compose", "app-blocker", "digital-wellbeing", "parental-control", "open-source"]
}

variable "required_approvals" {
  description = "Approving reviews required on main. Must stay 0: Qodo (the fallback reviewer) cannot approve, so a required approval would block fallback merges. The CodeRabbit/Qodo gate is enforced by docs/agents/PHASE1_EXECUTION.md."
  type        = number
  default     = 0
}

variable "required_status_checks" {
  description = "Status checks that must pass before merging to main."
  type        = set(string)
  # gradle-dependency-graph (dependency-graph.yml) is required on its own: it is a separate workflow, so security-pass cannot
  # see it, and a failed graph would leave dependency review without the Gradle snapshot.
  default = ["ci-pass", "security-pass", "gradle-dependency-graph"]
}
