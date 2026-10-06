output "backlog" {
  description = "Issue numbers and relationships, read by scripts/backlog/sync_project.py."
  value = {
    epics = { for id, e in github_issue.epic : id => { number = e.number, title = local.epics[id].meta.title } }
    stories = {
      for id, s in github_issue.story : id => {
        number  = s.number
        epic    = local.stories[id].meta.epic
        size    = local.stories[id].meta.size
        depends = local.stories[id].meta.depends
        human   = contains(local.stories[id].meta.labels, "needs-human")
      }
    }
  }
}
