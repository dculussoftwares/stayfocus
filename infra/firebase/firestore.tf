# Firestore (native mode), TTL for link tokens, composite indexes and the security rules release (M7-01).

locals {
  firestore_apis = [
    "firestore.googleapis.com",
    "firebaserules.googleapis.com",
    "firebaseappcheck.googleapis.com",
    "playintegrity.googleapis.com",
  ]
}

resource "google_project_service" "firestore" {
  for_each = toset(local.firestore_apis)

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false

  depends_on = [google_project_service.base]
}

resource "google_firestore_database" "default" {
  project     = var.project_id
  name        = "(default)"
  location_id = var.firestore_location
  type        = "FIRESTORE_NATIVE"

  # User data lives here: refuse accidental deletes (flip in a reviewed PR to remove).
  delete_protection_state = "DELETE_PROTECTION_ENABLED"
  deletion_policy         = "ABANDON"

  depends_on = [google_project_service.firestore, google_firebase_project.this]
}

# Link tokens expire after 5 minutes (plan section 7); Firestore deletes the documents after expiresAt.
resource "google_firestore_field" "link_token_ttl" {
  project    = var.project_id
  database   = google_firestore_database.default.name
  collection = "linkTokens"
  field      = "expiresAt"

  ttl_config {}

  # TTL fields don't need the built-in single-field indexes.
  index_config {}
}

# Composite indexes for the M7/M8 queries. The device subcollections are read per device (COLLECTION scope) and, for the
# parent's "everything pending" views, across devices (COLLECTION_GROUP scope).
locals {
  composite_indexes = {
    requests_status_created = {
      collection  = "requests"
      query_scope = "COLLECTION_GROUP"
      fields      = [["parentUid", "ASCENDING"], ["status", "ASCENDING"], ["createdAt", "DESCENDING"]]
    }
    alerts_dismissed_created = {
      collection  = "alerts"
      query_scope = "COLLECTION_GROUP"
      fields      = [["parentUid", "ASCENDING"], ["dismissed", "ASCENDING"], ["createdAt", "DESCENDING"]]
    }
    requests_device_status_created = {
      collection  = "requests"
      query_scope = "COLLECTION"
      fields      = [["status", "ASCENDING"], ["createdAt", "DESCENDING"]]
    }
    alerts_device_dismissed_created = {
      collection  = "alerts"
      query_scope = "COLLECTION"
      fields      = [["dismissed", "ASCENDING"], ["createdAt", "DESCENDING"]]
    }
    commands_status_created = {
      collection  = "commands"
      query_scope = "COLLECTION"
      fields      = [["status", "ASCENDING"], ["createdAt", "ASCENDING"]]
    }
  }
}

resource "google_firestore_index" "composite" {
  for_each = local.composite_indexes

  project     = var.project_id
  database    = google_firestore_database.default.name
  collection  = each.value.collection
  query_scope = each.value.query_scope

  dynamic "fields" {
    for_each = each.value.fields
    content {
      field_path = fields.value[0]
      order      = fields.value[1]
    }
  }
}

# Security rules are released from firebase/firestore.rules. Editing that file replaces the ruleset (the plan shows the
# diff) and moves the "cloud.firestore" release to it on merge.
resource "google_firebaserules_ruleset" "firestore" {
  project = var.project_id

  source {
    files {
      name    = "firestore.rules"
      content = file("${path.module}/../../firebase/firestore.rules")
    }
  }

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [google_firestore_database.default]
}

resource "google_firebaserules_release" "firestore" {
  project      = var.project_id
  name         = "cloud.firestore"
  ruleset_name = google_firebaserules_ruleset.firestore.name
}
