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

variable "release_manager_ids" {
  description = "GitHub user IDs allowed to create release tags and approve the release environment."
  type        = set(number)

  validation {
    condition     = length(var.release_manager_ids) > 0
    error_message = "At least one release manager ID is required to protect release tags and signing secrets."
  }
}
