// Static site for vesper.dculus.com, served from Cloudflare Workers static assets (wrangler.jsonc).
// Pages are built as files (privacy.html); Cloudflare serves them at clean URLs (/privacy) and redirects the old
// .html paths there, so links in older app versions keep working.
import { defineConfig } from "astro/config";
import react from "@astrojs/react";
import sitemap from "@astrojs/sitemap";

export default defineConfig({
  site: "https://vesper.dculus.com",
  trailingSlash: "never",
  build: { format: "file" },
  integrations: [react(), sitemap()],
});
