# Cloud Functions (2nd gen), deployed by Terraform only (M7-02). Never `firebase deploy`.
#
# Pipeline: the infra-firebase workflow runs `npm ci && npm run build` in firebase/functions, which writes one folder per
# function (dist/<name>/index.js + package.json). Terraform zips each folder, uploads it under a content-hashed name and
# points that function at it. A change to one function's code changes only its zip, so only that function is redeployed.
#
# Adding a function: export it from firebase/functions/src/functions/<name>.ts and add it to local.functions below.

locals {
  functions_apis = [
    "cloudfunctions.googleapis.com",
    "cloudbuild.googleapis.com",
    "run.googleapis.com",
    "eventarc.googleapis.com",
    "artifactregistry.googleapis.com",
  ]

  # kind "callable": an HTTPS callable. Cloud Run's invoker is public because the Functions framework checks App Check and
  # sign-in itself (see secureCallable). Event triggers (M8-05) get no public invoker and are added with their own kind.
  # roles: project roles for the function's own runtime service account, on top of logging.
  functions = {
    # Sample that proves the pipeline. Remove it once a real function exists (see src/functions/ping.ts).
    ping = {
      kind  = "callable"
      roles = ["roles/datastore.user"] # rate-limit counters in Firestore
    }
  }

  function_runtime_roles = {
    for pair in flatten([
      for name, f in local.functions : [
        for role in distinct(concat(["roles/logging.logWriter"], f.roles)) : { key = "${name}/${role}", name = name, role = role }
      ]
    ]) : pair.key => pair
  }
}

resource "google_project_service" "functions" {
  for_each = toset(local.functions_apis)

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false

  depends_on = [google_project_service.base]
}

# Zips of the function sources. Private; versions older than 30 days are deleted.
resource "google_storage_bucket" "functions_source" {
  project                     = var.project_id
  name                        = "${var.project_id}-functions-source"
  location                    = var.region
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  lifecycle_rule {
    condition {
      age = 30
    }
    action {
      type = "Delete"
    }
  }

  depends_on = [google_project_service.functions]
}

# One runtime service account per function, with only the roles it needs.
resource "google_service_account" "function_runtime" {
  for_each = local.functions

  project      = var.project_id
  account_id   = "fn-${replace(lower(each.key), "_", "-")}"
  display_name = "Function ${each.key} (runtime)"
}

resource "google_project_iam_member" "function_runtime" {
  for_each = local.function_runtime_roles

  project = var.project_id
  role    = each.value.role
  member  = "serviceAccount:${google_service_account.function_runtime[each.value.name].email}"
}

# Cloud Build runs as this account instead of the default compute service account.
resource "google_service_account" "functions_build" {
  project      = var.project_id
  account_id   = "functions-build"
  display_name = "Cloud Functions builds"
}

resource "google_project_iam_member" "functions_build" {
  project = var.project_id
  role    = "roles/cloudbuild.builds.builder"
  member  = "serviceAccount:${google_service_account.functions_build.email}"
}

data "archive_file" "function" {
  for_each = local.functions

  type        = "zip"
  source_dir  = "${path.module}/../../firebase/functions/dist/${each.key}"
  output_path = "${path.module}/.build/${each.key}.zip"
}

resource "google_storage_bucket_object" "function_source" {
  for_each = local.functions

  bucket = google_storage_bucket.functions_source.name
  name   = "${each.key}/${data.archive_file.function[each.key].output_md5}.zip"
  source = data.archive_file.function[each.key].output_path
}

resource "google_cloudfunctions2_function" "function" {
  for_each = local.functions

  project     = var.project_id
  name        = each.key
  location    = var.region
  description = "Stay Focused ${each.key} (${var.env})"

  build_config {
    runtime         = "nodejs22"
    entry_point     = each.key
    service_account = google_service_account.functions_build.id

    source {
      storage_source {
        bucket = google_storage_bucket.functions_source.name
        object = google_storage_bucket_object.function_source[each.key].name
      }
    }
  }

  service_config {
    service_account_email            = google_service_account.function_runtime[each.key].email
    available_memory                 = "256M"
    timeout_seconds                  = 60
    min_instance_count               = 0
    max_instance_count               = 10
    ingress_settings                 = "ALLOW_ALL"
    all_traffic_on_latest_revision   = true
    max_instance_request_concurrency = 1
  }

  depends_on = [
    google_project_service.functions,
    google_project_iam_member.function_runtime,
    google_project_iam_member.functions_build,
  ]
}

# Callables: anyone can reach the Cloud Run service; the framework rejects calls without a valid App Check token and
# ID token (enforceAppCheck, secureCallable). Event-triggered functions must not be given this binding.
resource "google_cloud_run_v2_service_iam_member" "callable_invoker" {
  for_each = { for name, f in local.functions : name => f if f.kind == "callable" }

  project  = var.project_id
  location = var.region
  name     = google_cloudfunctions2_function.function[each.key].service_config[0].service
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# Rate-limit counters (rateLimits/{action}_{uid}) expire after two windows.
resource "google_firestore_field" "rate_limit_ttl" {
  project    = var.project_id
  database   = google_firestore_database.default.name
  collection = "rateLimits"
  field      = "expireAt"

  ttl_config {}

  index_config {}
}
