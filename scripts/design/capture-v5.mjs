#!/usr/bin/env node
// Re-renders the v5 design references in design/screens-v5/ (one PNG per screen, 2x).
// The prototype loads its screens with fetch(), so it must be served over HTTP, not file://.
//
//   cd design && python3 -m http.server 8765 &      # from the repo root
//   npm i --prefix /tmp/sf-capture playwright-core@1.47.2
//   NODE_PATH=/tmp/sf-capture/node_modules node scripts/design/capture-v5.mjs
//
// Uses the installed Google Chrome (channel "chrome"); no browser download.
import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright-core');
const url = process.env.V5_URL ?? 'http://localhost:8765/Stay%20Focused%20v5%20Screens.dc.html';
const out = path.resolve(process.env.V5_OUT ?? 'design/screens-v5');

const browser = await chromium.launch({ channel: 'chrome' });
const page = await browser.newPage({ viewport: { width: 1800, height: 1200 }, deviceScaleFactor: 2 });
await page.goto(url, { waitUntil: 'networkidle' });
await page.waitForTimeout(8000); // fonts + every imported screen
for (const el of await page.$$('[data-screen-label]')) {
  const label = await el.getAttribute('data-screen-label');
  const phone = await el.$('div[style*="border-radius: 52px"], div[style*="border-radius:52px"]');
  const name = label.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/-+$/, '');
  await (phone ?? el).screenshot({ path: path.join(out, `${name}.png`) });
  console.log(name);
}
await browser.close();
