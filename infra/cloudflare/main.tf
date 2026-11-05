# The website (web/, Astro) on Cloudflare Workers static assets, at vesper.dculus.com.
#
# The dculus.com zone and its other records exist outside this module and are never managed here: this module only
# adds resources. Every resource has prevent_destroy, so a plan that would delete one fails instead.
#
# Terraform owns the Worker and its custom domain; .github/workflows/web.yml uploads the site (wrangler deploy) as new
# versions of this Worker. The custom domain creates the DNS record and certificate for the hostname.

resource "cloudflare_worker" "web" {
  account_id = var.account_id
  name       = var.web_worker_name

  # Served only on the custom domain, not on *.workers.dev. Matches workers_dev = false in web/wrangler.jsonc.
  subdomain = {
    enabled          = false
    previews_enabled = false
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "cloudflare_workers_custom_domain" "web" {
  account_id = var.account_id
  zone_id    = var.zone_id
  zone_name  = var.zone_name
  hostname   = var.web_hostname
  service    = cloudflare_worker.web.name

  lifecycle {
    prevent_destroy = true
  }
}
