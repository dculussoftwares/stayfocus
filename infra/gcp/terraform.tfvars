# Public configuration of the prod project. Secrets (billing account, OAuth client secret, App Check debug tokens)
# come from GitHub secrets as TF_VAR_* in .github/workflows/infra.yml.

project_id = "vesper-prod-31275"

# Signing-certificate fingerprints per app (public values). Today: the debug key. Add the Play App Signing
# fingerprints (Play Console > App integrity) when the apps are published.
sha1_fingerprints = {
  app  = ["69:38:3E:14:D2:26:49:38:FB:B2:74:BE:31:64:2F:42:E6:33:B7:A7"]
  kids = ["69:38:3E:14:D2:26:49:38:FB:B2:74:BE:31:64:2F:42:E6:33:B7:A7"]
}
sha256_fingerprints = {
  app  = ["0D:1D:88:64:D6:26:6C:14:AD:D4:7E:64:6D:C4:98:80:3B:17:68:3E:F5:9F:A4:D3:9F:26:E1:1D:92:71:1E:9D"]
  kids = ["0D:1D:88:64:D6:26:6C:14:AD:D4:7E:64:6D:C4:98:80:3B:17:68:3E:F5:9F:A4:D3:9F:26:E1:1D:92:71:1E:9D"]
}

# On: the debug key was regenerated on 2026-11-05, so its SHA-1 is not held by the deleted StayFocus project.
register_sha1_on_firebase_apps = true

# ENFORCED rejects requests without a valid App Check token; UNENFORCED only records metrics.
app_check_enforcement = "ENFORCED"

budget_amount        = 20
gemini_budget_amount = 5

# Gemini request caps, e.g. [{ metric = "generativelanguage.googleapis.com/generate_content_requests", unit = "/min/project", limit = 60 }].
gemini_quota_overrides = []
