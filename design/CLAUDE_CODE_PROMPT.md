# Prompt for Claude Code

Copy this folder into the root of your repo as `design/`, then paste the prompt below into Claude Code.

---

You are building **Stay Focused**, an open-source Android app (Play Store), Phase 1.

## Design source of truth
- `design/Stay Focused - All Screens.html`: open in a browser. Every Phase 1 screen, laid out in flow order. Match it closely: layout, spacing, type, colours, copy.
- `design/Stay Focused v5.dc.html`: the interactive prototype. Read its template (markup + inline styles) for exact values, and its logic class for state, flows and edge cases.
- `design/Stay Focused Phase Plan.dc.html`: phase scope. Build Phase 1 only.
- `design/screenshots/`: older v2 reference captures, for flow order only. Where they differ from v5, v5 wins.
- These HTML files are references. Don't ship them or embed a WebView. Rebuild every screen natively.

## Stack
- Kotlin, Jetpack Compose, Material 3 (themed to match the design rather than default Material).
- MVVM, Hilt, Room, DataStore, Kotlin Coroutines/Flow, Navigation Compose.
- Backend for accounts and device linking: Firebase Auth (Google sign-in) + Firestore + FCM. Keep it behind a repository interface so it can be swapped for a self-hosted backend later.
- Blocking: AccessibilityService (foreground app + browser URL detection), UsageStatsManager (screen time), overlay activity for the block screen, Device Admin (uninstall protection, Strict Mode), and a foreground service.
- QR: ML Kit barcode scanning + CameraX. Generate QR codes with ZXing.
- minSdk 26, targetSdk latest. Licence: GPL-3.0 (confirm with me).

## Design system to extract first
Before building screens, read `Stay Focused v5.dc.html` and create `ui/theme/`:
- `Color.kt`: every hex used (base white/off-white surfaces, ink `#000B1A`, orange `#FE5E0E`, violet `#4B3FD1`, pink, greens, greys). Name them semantically.
- `Type.kt`: the fonts from the `<helmet>` Google Fonts link, bundled as downloadable or local fonts, plus the size/weight scale used.
- `Shape.kt` / `Dimens.kt`: corner radii (pill buttons, 24–38dp cards), spacing scale.
- Reusable composables: pill button, glow/gradient card (radial gradients + grain), ruler slider, segmented chips, app icon tile, bottom tab bar, bottom sheet, toggle.
Show me the theme + component gallery (a Compose preview screen) before building feature screens.

## Phase 1 features (build in this order, one PR each)
1. Project setup, theme, component gallery.
2. Onboarding: welcome, Google sign-in, permissions system check, prominent Accessibility disclosure (required by Play policy).
3. Home: screen-time card, quick actions ("Take a break", "Link a child's phone"), active blocks.
4. Blocks: list + floating "New block" button. Manual wizard: type → apps → rule. Block types: always, schedule, daily limit, and **"use, then rest"** (allowed for X minutes after opening, then blocked for Y minutes).
5. AI block creation: free text → structured block rule. Use a pluggable `RuleParser` interface: an on-device keyword/regex parser as the default (works offline, open source), with an optional LLM provider the user supplies a key for. Always show the parsed rule for confirmation before saving.
6. Blocking engine + block screen overlay, Strict Mode.
7. Insights: daily/weekly screen time, per-app usage, hourly chart.
8. Device linking:
   - Each user signs in. Parent phone links a child phone.
   - **Only the child phone shows a QR code; only the parent phone scans.** The QR contains a short-lived one-time pairing token (expires in ~2 min), never a user ID or secret.
   - Child phone shows a consent screen before linking completes.
   - Parent can view the child's screen time and create, edit or pause blocks remotely. Sync via Firestore and push via FCM. The child device enforces blocks locally so they work offline.
   - Kids mode: simplified child home, block screen, unlink only with parent approval.

## How to work
- Ask me before adding any screen, feature or copy that isn't in the design.
- Keep copy verbatim from the design.
- After each step, run the app, compare against the matching screen in `Stay Focused - All Screens.html`, and list any differences.
- Write unit tests for the blocking rules (especially "use, then rest" timing) and the pairing token flow.
- Keep a `docs/PLAY_POLICY.md` checklist covering Accessibility API disclosure, Device Admin, foreground service type, data safety form, and the families policy (child accounts).

Start with step 1 and stop for my review.
