# Handoff: Stay Focused, Phase 1 (Android)

## Overview
Stay Focused is a free, open-source Android app that blocks distracting apps. In Phase 1 a user can:
- sign in with Google or email,
- block apps with 4 rule types (Time limit, Use then rest, Block during hours, Block now),
- create blocks step by step or by describing them to the AI,
- take a timed break,
- see screen-time insights,
- link a **child's phone** (parent → child only) by scanning a QR code, then control it remotely.

The approved visual direction is **v2**: a dark "instrument panel" look with a lime accent.

## About the design files
The files in this bundle are **design references built in HTML**. They are clickable prototypes that show the intended look and behaviour; they are not production code. Rebuild them natively in **Kotlin + Jetpack Compose (Material 3 as the base, fully custom-themed)**. Don't ship or wrap the HTML.

Open `Stay Focused Phase 1 Prototype v2.dc.html` in Chrome. It needs `support.js` in the same folder. The panel on the left jumps straight into each flow.

## Fidelity
**High fidelity.** Colours, type, spacing, radii, copy and interactions are final. Match them closely; convert px to dp at 1:1, since the phone frame is 372 × 826 dp of usable screen.

---

## Implementation plan (suggested order)

**Repo layout (Gradle multi-module).** Two apps share the core modules:

| Module | What it is |
|---|---|
| `:app` | Stay Focused (self and parent) |
| `:kids` | Stay Focused Kids (child companion) |
| `:core:blocking` | The blocking engine: AccessibilityService, overlay, rule evaluator |
| `:core:usage` | UsageStatsManager queries and aggregation |
| `:core:data` | Room database (blocks, rules, usage cache) and DataStore |
| `:core:sync` | Firebase Auth, Firestore, FCM |
| `:core:ui` | Theme, typography, components (dial, gauge, LED bars, toggles) |
| `:feature:*` | onboarding, home, block, devices, insights, account |

Why two apps: Google Play's stalkerware policy allows monitoring only in apps exclusively designed and marketed for parents. The `:kids` app carries the `IsMonitoringTool` flag, the persistent notification and the Accessibility declaration. The `:app` stays a self-control app. See section 8 of `Stay Focused Phase Plan.dc.html`.

**Milestones**
1. **Foundations.** `targetSdk = 36`, theme and type, navigation (4 tabs + sub-screens), Room schema.
2. **Blocking engine.** AccessibilityService that detects the foreground app, a blocking overlay activity, and a rule evaluator for the 4 rule types. Block now and Take a break come first, then daily/hourly limits, then "Use, then rest" (per-app open sessions with a cooldown timer), then schedules.
3. **Usage and Insights.** UsageStatsManager: today's total, hourly buckets, per-app minutes and opens, unlock count from `KEYGUARD_HIDDEN` events.
4. **Block wizard + dial.** A 3-step wizard (type → apps → rules) with the rotary dial component and a summary sentence.
5. **Onboarding + permissions.** Welcome, sign-in, then the permission check: Usage access, Accessibility (with its own disclosure screen), overlay and notifications. Keep battery-optimisation guidance for OEMs.
6. **Accounts.** Firebase Auth (Google + email, password reset) and account deletion, both in-app and via a web page.
7. **Linking.**
   - The Kids app shows a QR code holding a one-time token (Firestore doc, 5-minute TTL).
   - The parent scans it with CameraX + ML Kit barcode scanning.
   - Both phones show the same 6-digit code, and the child taps Allow.
   - The child's phone then gets a Firestore device doc under the parent's account.
8. **Remote controls.**
   - The parent writes commands to `devices/{id}/commands` (block/unblock app, start/stop focus, approve request).
   - An FCM data message wakes the child's phone.
   - The child reports status (battery, current app, online) every 1–2 min and on change.
   - Tamper alerts: the child reports when a permission is lost; the parent is notified.
9. **AI block builder.**
   - The user's sentence is sent to Gemini via Firebase AI Logic with a strict JSON schema (see `AI_SYS` in the prototype).
   - If that fails, fall back to the on-device rule parser (`localParse`).
   - The AI's result only fills the form at wizard step 3, marked "AI DRAFT". The user confirms before anything turns on.
10. **Play readiness.** Data safety form, Accessibility and foreground-service (`specialUse`) declarations with demo videos, a privacy-policy URL, and a closed test with 12 testers for 14 days on a new personal account.

---

## Screens

The tab bar has 4 tabs: **Home · Block · Devices · Insights**. Account opens from the avatar on Home.

| Screen | Purpose | Key content |
|---|---|---|
| Welcome | First run | Headline "Take back your time." (lime), "Get started", "This is my child's phone" |
| Sign in / Create account | Auth | Google button, email + password, inline errors, "Forgot password?" |
| System check (permissions) | Grant 4 permissions | 4 rows, each with an LED, a 1-line reason and an Allow button, plus a progress bar (x/4). Tapping Accessibility opens the disclosure sheet. |
| Accessibility disclosure (sheet) | Play-required consent | What it reads / Why / Never; "Agree and open Settings" / "No thanks" |
| Home | Actions + status | Header (date, greeting, avatar). Row: **+ New block** (white, primary) and **AI Describe**. Screen-time gauge (240° arc, 0–4 h scale, average marker), 24-column LED hourly bars, 3 stats (launches / unlocks / blocked). **Take a break** card (lime), or a live countdown while a break runs. Active blocks for this phone with toggles. Linked phones. |
| Take a break (sheet) | Timed block-all | Rotary dial (5–120 min, step 5), presets 15m/30m/1h/2h, "Start ___ break" |
| Block | Manage blocks | "Blocking on" target picker (This phone / each child phone), big lime **New block** button ("on {target}"), AI box (toggle), Blocks / All apps tabs, template cards |
| Block wizard 1/3 | Pick rule type | Time limit · Use, then rest · Block during hours · Block now, plus "Rather just describe it?" (AI). An ON pills row picks the target phone. |
| Block wizard 2/3 | Pick apps | 4-column app grid with check tiles; the counter shows how many are selected |
| Block wizard 3/3 | Rules | Dial (limit: per day/hour; cycle: "Use for" / "Then locked" tabs editing the same dial; now: up to 8 h), schedule presets, day chips M–S, live summary sentence, "Turn on block" |
| Devices | Linked phones | This-phone row, device cards (name, online LED, today / battery / current app, alert line) |
| Link a child's phone | Pair | One option only: **Scan their code**, which opens the camera |
| Scan | Camera | Corner brackets, prompt text |
| Confirm link | Match code | 6-digit code, editable device name, note that the child can unlink anytime |
| Child's phone (remote) | Parent controls | Online/battery/current app. Tamper alert (red) with Remind / Dismiss. Unlock request (amber) with 15m / 30m / 1h / No. Focus session (25/50/90). Blocks on this phone, **New block for {name}**, per-app lock toggles, Unlink. |
| Insights | Usage | Day stepper, Screen time / Opens / Unlocks tabs, big total, 24 × 10 LED chart with average line, ranked app list |
| Account | Profile | Name/email, permission health, open-source links, Privacy policy, Sign out, **Delete account and all data** |
| Kids: Link | Child shows QR | QR code, "Waiting for scan · 4:52" |
| Kids: Consent | Allow parent | Matching code, list of what the parent can do, "Allow and link" |
| Kids: Home | Child view | Persistent notification ("managed by…"), today's total, rules set by the parent, unlink |
| Kids: Blocked overlay | Cooldown | "Instagram · LOCKED", reason, live countdown to reopen, "Ask {parent} for more time" → 15/30/60 min → "Request sent" |

## Interactions and behaviour
- **Dial:** drag anywhere on the ring. angle = atan2 from the centre, clockwise from 12 o'clock. value = round(angle / 360 × max / step) × step, clamped to at least one step. Presets set the value directly. The centre font shrinks for long values (54 → 44 → 36 sp).
- **Sheets:** slide up in 300 ms with `cubic-bezier(.2,.8,.2,1)` and a 60% black scrim; tap outside to close.
- **Screen enter:** fade + 8 dp translate-Y over 350 ms.
- **Online LED:** pulse, 1.6–2 s loop.
- **Toast:** bottom, 2.6 s, light background with dark text.
- **Validation:** email must match `\S+@\S+\.\S+`; password needs 6+ characters; the wizard needs at least 1 app.
- **AI:** the button shows a spinner and "READING YOUR REQUEST". The result opens wizard step 3 with the AI DRAFT banner, and "Change" goes back to step 1.

## State (for ViewModels)
- **Blocks:** Profile/Block `{id, target: me|deviceId, type: limit|cycle|schedule|now, apps[], mins, period, use, rest, range, durationMins, days[7], on}`
- **Break:** `{endsAt, lengthMins}`
- **Device:** `{id, name, model, battery, currentApp, online, alerts[], requests[], focusEndsAt, apps[{pkg, mins, blocked}]}`
- **Permissions:** `{usage, accessibility, overlay, notifications}`

## Design tokens (v2)
**Colours**

| Role | Hex |
|---|---|
| Background | `#0E0F0D` |
| Panel | `#171915` |
| Panel gradient | `#1A1C17 → #141611` |
| Track / unlit | `#262922` |
| Hairline | `rgba(255,255,255,.06–.14)` |
| Text | `#F2F4EC` |
| Secondary text | `#A6AB9D` |
| Tertiary text | `#6E7367` |
| Accent | `#C6F432` |
| Accent hover | `#D6FF55` |
| Alert | `#FF5A47` (text `#FF7A66`) |
| Warning | `#FFB547` |
| App tints | `#FF9A8B`, `#FF7A66`, `#FFB547`, `#7EE0A1`, `#9DC3FF`, `#D9DCD2`, `#B9A7FF` |

**Type**
- Display: **Clash Display** 600, letter-spacing −0.02 to −0.03em; sizes 22 / 26–34 / 38–54.
- Body: **Satoshi** 400–800; 12.5–16.
- Labels and readouts: **Geist Mono** 500–700, 10–11 sp uppercase, tracking +0.08–0.12em; numbers 16–60.

**Radii:** chips 7–10 · inputs and buttons 14–16 · cards 18–22 · hero cards 26–28 · sheets 32 · tab bar 24.

**Spacing:** screen padding 18; gaps 6 / 8 / 10 / 12 / 14 / 16 / 22.

**Primary button:** height 56, radius 16, lime background, dark text 16/800.

## Assets
- No raster assets.
- App icons in the prototype are letter tiles; use real launcher icons from PackageManager.
- QR codes are generated (use the ZXing or ML Kit encoder).
- Line icons are simple 24-px strokes; swap them for Material Symbols Rounded or Lucide.

## Files
- `Stay Focused Phase 1 Prototype v2.dc.html`: the approved design (open it with `support.js` next to it).
- `Stay Focused Phase Plan.dc.html`: phases, decisions, and the Play Store compliance checklist.
- `support.js`: the runtime the prototype needs.
