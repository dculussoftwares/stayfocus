# Cloud Monitoring alerts (M10-04): Cloud Functions error rate and FCM send failures. Same resources in dev and prod;
# only the notification email differs (alert_email). Without an email the policies still exist and show in the console.

resource "google_project_service" "monitoring" {
  for_each = toset(["monitoring.googleapis.com", "logging.googleapis.com"])

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_monitoring_notification_channel" "email" {
  count = var.alert_email != "" ? 1 : 0

  project      = var.project_id
  display_name = "Stay Focused ${var.env} alerts"
  type         = "email"
  labels = {
    email_address = var.alert_email
  }

  depends_on = [google_project_service.monitoring]
}

locals {
  alert_channels = [for c in google_monitoring_notification_channel.email : c.id]
}

# 2nd-gen functions run as Cloud Run services; a 5xx response is an unhandled error (callables return 4xx for bad input).
resource "google_monitoring_alert_policy" "function_errors" {
  project      = var.project_id
  display_name = "Functions error rate (${var.env})"
  combiner     = "OR"

  conditions {
    display_name = "5xx responses from Cloud Functions"

    condition_threshold {
      filter          = "resource.type = \"cloud_run_revision\" AND metric.type = \"run.googleapis.com/request_count\" AND metric.labels.response_code_class = \"5xx\""
      comparison      = "COMPARISON_GT"
      threshold_value = var.function_error_threshold
      duration        = "0s"

      aggregations {
        alignment_period     = "300s"
        per_series_aligner   = "ALIGN_DELTA"
        cross_series_reducer = "REDUCE_SUM"
        group_by_fields      = ["resource.label.service_name"]
      }

      trigger {
        count = 1
      }
    }
  }

  notification_channels = local.alert_channels

  alert_strategy {
    auto_close = "3600s"
  }

  documentation {
    mime_type = "text/markdown"
    content   = "A Cloud Function returned more than ${var.function_error_threshold} server errors in 5 minutes. Check the function's logs (they hold function names and outcome codes only, no personal data)."
  }

  depends_on = [google_project_service.monitoring]
}

# notify.ts logs "push batch failed" (outcome send-failed) when an FCM multicast call throws.
resource "google_logging_metric" "fcm_failures" {
  project = var.project_id
  name    = "fcm_send_failures"
  filter  = "resource.type = \"cloud_run_revision\" AND severity = WARNING AND jsonPayload.message = \"push batch failed\""

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    unit        = "1"
  }

  depends_on = [google_project_service.monitoring]
}

resource "google_monitoring_alert_policy" "fcm_failures" {
  project      = var.project_id
  display_name = "FCM send failures (${var.env})"
  combiner     = "OR"

  conditions {
    display_name = "FCM push batches failing"

    condition_threshold {
      filter          = "resource.type = \"cloud_run_revision\" AND metric.type = \"logging.googleapis.com/user/${google_logging_metric.fcm_failures.name}\""
      comparison      = "COMPARISON_GT"
      threshold_value = var.fcm_failure_threshold
      duration        = "0s"

      aggregations {
        alignment_period     = "900s"
        per_series_aligner   = "ALIGN_DELTA"
        cross_series_reducer = "REDUCE_SUM"
      }

      trigger {
        count = 1
      }
    }
  }

  notification_channels = local.alert_channels

  alert_strategy {
    auto_close = "3600s"
  }

  documentation {
    mime_type = "text/markdown"
    content   = "More than ${var.fcm_failure_threshold} FCM push batches failed in 15 minutes, so parents may not receive unlock requests or tamper alerts. Check the notifyParent and notifyParentAlert logs and the FCM status page."
  }

  depends_on = [google_project_service.monitoring]
}
