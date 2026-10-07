output "projects" {
  description = "Per environment: project id, WIF provider and deploy service account, for the GitHub variables."
  value = {
    for env, cfg in var.environments : env => {
      project_id         = google_project.env[env].project_id
      wif_provider       = google_iam_workload_identity_pool_provider.github[env].name
      deploy_service_acc = google_service_account.deploy[env].email
      plan_service_acc   = google_service_account.plan[env].email
    }
  }
}
