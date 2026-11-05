# Public configuration of the prod project. Secrets (billing account, OAuth client secret, App Check debug tokens)
# come from GitHub secrets as TF_VAR_* in .github/workflows/infra.yml.

project_id = "vesper-prod-31275"

# Signing-certificate fingerprints per app (public values). Today: the debug key. Add the Play App Signing
# fingerprints (Play Console > App integrity) when the apps are published.
sha1_fingerprints = {
  app  = ["19:AB:1D:9D:74:8E:98:E1:1B:36:AC:FA:E5:C0:BE:B2:2F:A9:62:94"]
  kids = ["19:AB:1D:9D:74:8E:98:E1:1B:36:AC:FA:E5:C0:BE:B2:2F:A9:62:94"]
}
sha256_fingerprints = {
  app  = ["33:DE:AB:3F:93:C9:25:E0:A1:52:F3:40:8C:D4:C2:C4:9B:B8:EA:6D:01:1A:D5:F4:5C:EF:92:92:95:97:22:2B"]
  kids = ["33:DE:AB:3F:93:C9:25:E0:A1:52:F3:40:8C:D4:C2:C4:9B:B8:EA:6D:01:1A:D5:F4:5C:EF:92:92:95:97:22:2B"]
}

# Off until the deleted StayFocus project releases the debug key's OAuth clients (purged by 2026-12-05). Turn on to
# enable Google sign-in.
register_sha1_on_firebase_apps = false

# ENFORCED rejects requests without a valid App Check token; UNENFORCED only records metrics.
app_check_enforcement = "ENFORCED"

budget_amount        = 20
gemini_budget_amount = 5

# Gemini request caps, e.g. [{ metric = "generativelanguage.googleapis.com/generate_content_requests", unit = "/min/project", limit = 60 }].
gemini_quota_overrides = []
