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
