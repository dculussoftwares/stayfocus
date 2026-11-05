# Copy these into infra/prod.env (public values; the workflows read that file).
output "prod_env" {
  description = "Contents of infra/prod.env."
  value       = <<-EOT
    GCP_PROJECT_ID=${google_project.this.project_id}
    GCP_WIF_PROVIDER=${google_iam_workload_identity_pool_provider.github.name}
    GCP_DEPLOY_SA=${google_service_account.deploy.email}
    GCP_PLAN_SA=${google_service_account.plan.email}
    GCP_PLAY_SA=${google_service_account.play.email}
    TF_STATE_BUCKET=${google_storage_bucket.tfstate.name}
  EOT
}
