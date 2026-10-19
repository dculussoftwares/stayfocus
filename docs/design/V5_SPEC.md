# v5 redesign: spec for the UI migration (epics M11–M13)

This file turns the v5 design in `design/` into rules an agent can build from. Read it before any M11/M12/M13 story.
Phase 1 shipped the **v2** look (dark "instrument panel", lime accent). **v5** replaces it: light surfaces, Urbanist,
gradient "glow" cards with grain, dot-matrix numbers, tick-ruler sliders, a floating pill tab bar.

## 0. Sources and precedence

| Source | Use it for |
|---|---|
| `design/Stay Focused v5.dc.html` | **Source of truth.** Markup + inline styles = exact values; the `<script>` block (line 509 on) = copy, maths, state and edge cases. |
| `design/Stay Focused v5 Screens.dc.html` | The 24 screens in flow order (each imports the prototype with a `start=` key). Serve over HTTP to open it (§1). |
| `design/screens-v5/NN-*.png` | 2x renders of those 24 screens (390 × 844 dp frame). Compare your Roborazzi output against these. |
| `design/Stay Focused - All Screens.html` | Self-contained bundle of the same 24 screens (opens from disk). |
| `design/README.md`, `design/screenshots/` | **v2, superseded.** Flow order only. Where they differ from v5, v5 wins. |
| `design/CLAUDE_CODE_PROMPT.md` | Visual guidance only. Its stack, feature and backend notes were written for a fresh build and **don't apply**: the app exists. Keep the shipped architecture (Firebase AI Logic for AI, the current pairing-token TTL, no Device Admin/Strict Mode, etc.). |
| `design_handoff_stay_focused_phase1/` | v2 handoff. Still the reference for screens v5 doesn't cover (see §7, epic M13). |

**Strict v5 (decision):** M11/M12 build what v5 shows, as v5 shows it. Shipped features that v5 doesn't show are
**extras**, handled in epic M13 (§7). Copy in v5 is final: take strings verbatim from the prototype. Sample data in the
prototype (`Sam`, `Aarav's phone`, `Pixel 7a`, `Sunday, 18 October`, the minute values) is placeholder: bind real data.

## 1. Reading the prototype

Screen → lines in `Stay Focused v5.dc.html` → reference PNG → `start=` key:

| Screen | Lines | PNG | start |
|---|---|---|---|
| Welcome | 33–46 | 01 | `welcome` |
| Sign up | 48–62 | 02 | `signin` |
| Permissions | 64–81 | 03 | `perms` |
| Accessibility disclosure sheet | 484–497 | 04 | `a11y` |
| Home (+ break running) | 83–155 | 05, 07 | `home`, `breakOn` |
| Take a break sheet | 470–482 | 06 | `break` |
| Blocks tab (+ FABs 464–469) | 157–189 | 08, 09 | `blocks`, `blocksKid` |
| New block wizard (3 steps, AI draft banner) | 249–318 | 10–13, 15 | `new0`, `new1`, `new2`, `newSched`, `aiDraft` |
| Describe with AI | 320–337 | 14 | `ai` |
| Blocked app screen | 416–427 | 16 | `blocked` |
| Family tab | 191–217 | 17 | `family` |
| Child's phone (remote) | 339–383 | 18 | `child` |
| Scan code | 385–400 | 19 | `scan` |
| Codes match | 402–414 | 20 | `confirm` |
| Insights day / week | 219–247 | 21, 22 | `insights`, `insightsWeek` |
| Kids: show code | 429–440 | 23 | `kidqr` |
| Kids: allow parent | 442–456 | 24 | `kidok` |
| Tab bar | 458–462 | — | — |
| Toast | 499–501 | — | — |

Helpers in the script: `dm()` dot-matrix numerals (line 531), `dotGrid()` (534), `grain` (535), `breakArt` (544),
`linkArt` (546), `pulseGreen` (550), `scanLine` (551), `ruler()` (566), `viewBlock()` (585), summaries (611),
`localParse` (569, already ported in M9-02), constants `APPS`/`TYPES`/`SCHED` (514–523).

To open the screens board: `cd design && python3 -m http.server 8765`, then http://localhost:8765/Stay%20Focused%20v5%20Screens.dc.html.
To re-render the PNGs: `scripts/design/capture-v5.mjs` (instructions in its header).

## 2. Migration strategy (how every story works)

**Strangler, screen by screen.** The v2 design system stays until the last story (M13-07) removes it, so `main` always
builds and every screen keeps working.

- The v5 design system lives in `:core:ui` under the package **`com.dculus.stayfocused.core.ui.v5`**
  (`v5.theme`, `v5.components`, `v5.charts`, `v5.art`). Don't edit v2 files in `core.ui.theme` / `core.ui.components`
  except to fix a bug.
- A migrated screen wraps its content in **`V5Screen { }`** (M11-01). It provides `V5Theme` (tokens + a light Material 3
  `colorScheme` for any M3 widget), the `#EDEDEB` background, edge-to-edge insets and the status-bar icon colour
  (white on the "dark" screens: welcome, scan, blocked, kids QR; ink elsewhere). Unmigrated screens keep the v2 theme
  from the app root.
- Navigation routes, ViewModels, repositories and engine code stay. A screen story changes the Composables and the
  UI state they need; if the ViewModel lacks a value v5 shows, add it to the ViewModel (tests first).
- **Extras rule.** If the shipped screen has an element v5 doesn't show, **don't delete the behaviour**. Keep it working,
  render it with v5 components in the least prominent place (end of the screen, or a text link), keep its current copy,
  and mark it `// v5-extra: <M13 story id>`. M13 decides its final form. List these in the PR.
- **Screenshots:** each screen story re-records the Roborazzi baselines it changes (`./gradlew recordRoborazziDebug`)
  and puts its render next to the matching `design/screens-v5/` PNG in the PR description.
- **E2E:** if copy that a Maestro flow taps changes (e.g. the `DEVICES` tab becomes `Family`), update the flow in
  `e2e/flows/` in the same PR.
- **Dimensions:** the prototype phone is 390 × 844 with a 46 px fake status bar. 1 px = 1 dp, 1 px font = 1 sp. Top
  padding in the prototype includes the status bar: use `statusBars` inset + (prototype top − 46) dp
  (e.g. Home 54 → inset + 8 dp). Bottom padding 120 on tab screens leaves room for the tab bar; use the
  `navigationBars` inset + tab-bar height instead of a fixed 120.

## 3. Tokens

### Colours (`V5Colors`)
| Token | Hex | Where |
|---|---|---|
| `background` | `#EDEDEB` | Screen background |
| `surface` | `#FFFFFF` | Cards, inputs, round icon buttons |
| `surfaceRaised` | `#F8F8F6` | Sheets, "Block apps" tile |
| `ink` | `#000B1A` | Text, primary buttons, segmented containers, toggle-on track |
| `textSecondary` | `#5B6472` | Sub-text, captions |
| `textTertiary` | `#3A4250` | AI banner text, board labels |
| `onInkMuted` | `#C9CDD3` | Unselected tab/segment text on ink; scan sub-text |
| `hairline` | `#D3D4D6` | Dividers, sheet handle, toggle-off track, unlit ticks |
| `track` | `#ECECEA` | Usage-bar track, secondary button, unselected day chip |
| `orange` | `#FE5E0E` | Accent: break, New block, AI, tick progress |
| `orangeGlow` | `#FF9A3C` / `#FF7A2E` / `#F7934F` / `#FEBE42` | Gradient stops |
| `violet` | `#4B3FD1` (glow `#9B8CFF`, bar tint `#E4DFFF`, pill border `#E3DFFA`) | Home hero, Insights |
| `pink` | `#E8336F` (glow `#EC5A8A`, `#F07FA2`, `#EA4D80`, `#E26490`, `#EC6A93`, `#D42563`, `#E77A9E`, scan `#F27BA1`, text `#C21F59`) | Family, child, Kids |
| `green` | `#2F9A3C` (glow `#9CCB4A`); success `#2F9E45` | "Use, then lock" type; permission done |
| `online` | `#34C759` dot, pill bg `#E8F5EA`, text `#1F7A33` | Online pill |
| `red` | `#E5242B` (badge/tags); error text `#D12A2A`; destructive text `#C21F26` | Target tag, alerts, unlink |
| `lime` | `#D8F23C` | Break-art centre tick, link-art dot |
| `scanBackground` | `#0D0F12` | Scan screen |
| `inkGlow` | `#2C3A50` | "Block now" type gradient |
| `google` | `#4285F4` | "G" glyph |

App brand colours for letter tiles (fallback when no launcher icon): `APPS[].c` in the script (Instagram `#E1306C`,
TikTok `#111318`, YouTube `#FF0033`, Snapchat `#FFD400` with ink letter, …). Unknown apps: hash the package into a fixed
palette built from the colours above.

### Gradients (`V5Gradients`, CSS radial-gradient → Compose `Brush.radialGradient` over a solid base)
| Name | Definition (prototype) | Used by |
|---|---|---|
| `welcome` | `radial(70% 50% at 80% 75%, #FEBE42 → transparent 70%)`, `radial(90% 70% at 10% 20%, #FF7A2E → transparent 70%)`, base `#FE5E0E` | Welcome |
| `blocked` | `radial(60% 40% at 50% 40%, #FF7A2E → t 75%)`, `radial(80% 50% at 50% 100%, #FEBE42 → t 70%)`, base `#FE5E0E` | Blocked app |
| `orange` | `radial(60–80% … at 85% 20% / 25% 20%, #FF9A3C → t 70%)`, base `#FE5E0E` | New block card, Unlocks tile, Focus tile, Time-limit type |
| `orangeButton` | `radial(70% 120% at 20% 0%, #FF9A3C → t 70%)`, base `#FE5E0E` | "Start … break", "Build my block" |
| `breakTile` | `radial(50% 42% at 50% 60%, #FE5E0E 0%, #FF7A2E 45%, #F7934F 100%)` | Home "Take a break" |
| `violet` | `radial(80% 70% at 75% 15%, #9B8CFF → t 70%)`, base `#4B3FD1` | Home hero, Insights card, Blocked tile |
| `pinkCard` | `radial(70% 90% at 90% 40%, #E8336F 0%, #EA4D80 55%, #E26490 100%)` | Home child card |
| `pinkLink` | `radial(50% 45% at 50% 58%, #E8336F 0%, #EC5A8A 50%, #E77A9E 100%)` | Family "Link a child's phone" |
| `pinkHero` | `radial(70% 50% at 50% 0%, rgba(255,255,255,.3) → t 70%)` over `linear(180°, #EC6A93 0%, #E8336F 60%, #D42563 100%)` | Child's phone hero |
| `kidsQr` | `radial(70% 45% at 50% 45%, #EC5A8A → t 75%)`, base `#E8336F` | Kids show code |
| `scan` | `radial(60% 40% at 50% 48%, rgba(232,51,111,.35) → t 70%)`, base `#0D0F12` | Scan |
| Rule types (`TYPES[].bg`) | limit = orange; cycle `radial(80% 70% at 75% 15%, #9CCB4A)` on `#2F9A3C`; schedule `radial(80% 70% at 25% 15%, #F07FA2)` on `#E8336F`; now `radial(80% 70% at 75% 15%, #2C3A50)` on `#000B1A` | Wizard type tiles + step 3 card |

**Grain:** every gradient card has a noise overlay (`grain`, line 535): fractal noise, white, opacity .16,
`soft-light` blend. Build it once as a tiled 180 × 180 bitmap from a **fixed seed** (deterministic screenshots), drawn
with `BlendMode.Softlight`. **Glass:** white .18 fill, 1 dp white .3 border, background blur 16 (on API < 31 skip the
blur; the fill alone is fine).

### Type (`V5Type`): Urbanist (OFL), weights 200–700
Bundle Urbanist (variable font, OFL) in `:core:ui` res/font and add its licence to `docs/licenses/`. No runtime download.
JetBrains Mono appears only on the design board, not in the app.

| Style | Size / weight / line / tracking | Examples |
|---|---|---|
| `displayXL` | 92 / 200 / .95 / −.04em | Ruler value in wizard (88 in break sheet) |
| `displayL` | 66 / 200 / 1 / −.03em | Insights total ("2 h 33 m"), schedule "22:00–07:00" (58) |
| `hero` | 62 / 300 / .98 / −.025em | "Take back your time." |
| `title` | 38 / 300 / −.02em | Tab titles: Blocks, Family, Insights |
| `headline` | 34–36 / 300 / 1.05 / −.02em | Onboarding/linking headlines |
| `heading` | 28–30 / 400 | Wizard step title, sheet titles, "New block" (28/400) |
| `cardTitle` | 20–22 / 500 / 1.12 | "Block apps", child name |
| `rule` | 22 / 300 / 1.2 | Block rule sentence; kid stats (22–24 / 300) |
| `bodyL` | 16–17 / 500–600 | Buttons (17/600), list titles |
| `body` | 15 / 400–600, line 1.4–1.45 | Sub-text |
| `label` | 13–14 / 500–600 | Captions, chips, tab labels (15/600) |
| `tag` | 12 / 700 | Tags ("AI draft", "Request") |

### Shape, spacing, elevation
- Radii: tags 13–15 · day chip/round buttons = circle · inputs 24 · segmented 24–28 · cards 28–36 · hero cards 36–38 ·
  sheets 40 (top) · tab bar 32 · app tiles 13 (38 dp), 16 (48 dp), 26 (76 dp). Buttons are full pills (r = h/2).
- Heights: primary button 62 (sheet buttons 60, secondary 44–50) · input 58 · round icon button 46 · tab 50 · FAB 58.
- Screen padding 14 (tabs, wizard) or 18 (onboarding, linking) or 22 (kids, blocked). Gaps 4/6/8/10/12/14/16/18/22.
- Shadows: soft ink shadows, e.g. `0 12px 30px -20px rgba(0,11,26,.35)` → Compose `shadow(elevation≈8.dp,
  ambient/spot = ink @ .2–.35)`. Orange FAB: spot colour `rgba(254,94,14,.6)`.

### Motion
| What | Spec |
|---|---|
| Sheet | slide up 300 ms, `cubic-bezier(.2,.8,.2,1)`; scrim ink @ .4; tap outside closes |
| Toast | bottom 96 dp, ink bg, white 15/500, r24, padding 14×20, max width 320, 2.6 s |
| Online pulse (`sfPulse`) | ring scale .7 → 1.8, opacity .9 → 0, 1.8 s loop (break-art target 2.4 s) |
| Spinner (`sfSpin`) | 18 dp, 2 dp ring white .4, top white, .8 s linear |
| Link art dash (`sfDash`) | dash offset −16 per 1.1 s; lime dot blink 1 → .25, 1.6 s |
| Scan line (`sfScan`) | 2 dp line, pink glow, top 14% ↔ 84%, 2.4 s ease-in-out |
| Screen enter | keep the shipped fade + 8 dp rise |

All infinite animations stop under "Remove animations" (`ANIMATOR_DURATION_SCALE == 0`) and in screenshot tests.

## 4. Components (M11)

| Component | Spec (prototype) | Story |
|---|---|---|
| `V5Theme`, `V5Screen`, `GrainOverlay`, gradients | §3 | M11-01 |
| `PillButton` | variants: `Primary` (ink/white), `White` (white/ink), `Orange` (orangeButton gradient + grain), `Glass` (white .2, border .35–.4), `Secondary` (track/ink), `Text` (transparent, 15/600, optional underline); h 62/60/50/46/44; optional leading icon; `loading` = spinner + label; disabled = opacity .35–.4 | M11-02 |
| `RoundIconButton` | 46 dp white circle, 18 dp stroke icon; optional red badge 9 dp with 2 dp white border (bell); dark variant white .14 on scan | M11-02 |
| `V5TextField` | h58, r24, white, 16 sp ink, padding 20, placeholder secondary; password variant; error line 14/600 `#D12A2A` | M11-02 |
| `V5Toggle` | 52 × 32 track r16, padding 3; on = ink / off = hairline; knob 26 white with shadow; `Role.Switch` | M11-02 |
| `SegmentedPill` | ink container r28 (or white .2 on gradients), padding 4, gap 2–4; selected = white/ink, other = transparent/`onInkMuted` (white on gradient); heights 46 / 40 / 38 | M11-02 |
| `DayChips` | 7 × 40 dp circles, on = ink/white, off = track/secondary; at least one stays on | M11-02 |
| `Tag` | h26–30, r = h/2, 12–13 sp 600–700; variants: target (red + white 6 dp dot), status (Protection off red, Request orange, AI draft orange + sparkle), online pill (`OnlinePill` with `PulseDot`, `#E8F5EA`/`#1F7A33`, or white .22 on pink) | M11-02 |
| `AppIconTile`, `AppIconStack` | launcher icon (shipped `AppIcon` loader) clipped to r≈.34·size, letter fallback on brand colour; stack: overlap −8 dp, 2 dp white border, max 3 | M11-02 |
| `V5Card` | white card r28–36, padding 16–18; `GlowCard(gradient, radius)` with grain + clip; `GlassTile` | M11-03 |
| `ActionTile` | 204 dp tall, r36, title 20/500 top-left, 46 dp white "+" circle top-right, content slot bottom | M11-03 |
| `DotMatrixText` | 5 × 7 glyphs `F` (digits and `:`; space = 2 cells); dot pitch `s`, radius .34 s; sizes used s = 4, 5, 6, 8, 9; `Numerals.Thin` variant = Urbanist 200 at 9·s; `contentDescription` = readable text | M11-04 |
| `TickRuler` | 45 ticks (N = 44); current tick 4 × 70, passed 2 × 46, ahead 2 × 26, r2; colours per use (white on cards; break sheet: passed orange, ahead `#C9CBCF` @ .5, current ink); drag anywhere: value = round((min + f·(max−min))/step)·step, clamped; labels row min / "Drag to set" / max | M11-05 |
| `DotGrid` | values per column, `rows` (6), pitch 10, lit r 2.2 @ .85 (highlight column @ 1), unlit r 1.4 @ .32; fills width | M11-06 |
| `TickProgress` | 40 ticks, 2 dp wide; lit 30 dp orange, unlit 14 dp hairline; fraction = used / limit | M11-06 |
| `HourTicks` | child usage 6:00–23:59, 3 ticks per hour, h = max(3, v/25·34) dp, pink if > 0 else hairline; labels 6 AM / 12 PM / 6 PM / 11 PM | M11-06 |
| `GlassBarChart` | day = 24 bars gap 4 r5, week = 7 bars gap 10 r16; track white .14 + 1 dp inner border; bar fills: high (> 12 min/h) = white → `#E4DFFF` with top highlight, low = white .55 → .35, current = orange gradient `#FFB27A → #FE7A2E → #FE5E0E` + orange glow; dashed avg line (1.5 dp white @ .5); `Avg …` pill top-right | M11-06 |
| `UsageRow` | 38 dp tile, name 15/600, time secondary, 4 dp bar (track / ink) | M11-06 |
| `V5BottomSheet`, `V5Toast`, motion | §3 Motion; handle 40 × 5 hairline | M11-07 |
| Icons + art | stroke icons from the prototype SVGs (back, close, plus, bell, arrow, sparkle, block, lock, timer, check) as `ImageVector`s; `BreakArt`, `LinkArt`, `ScanBrackets` + `ScanLine`, `Spinner`, `PulseDot` | M11-08 |
| `PillTabBar` | floating: left/right 14, bottom 22 (+ nav inset), padding 5, r32, ink @ .92 + blur 20, shadow; 4 tabs h50 r25, selected white/ink, else transparent/`onInkMuted`, 15/600; labels **Home · Blocks · Family · Insights** | M11-09 |

Every component: `@Preview`, Roborazzi test, ≥ 48 dp touch target (pad smaller visuals), TalkBack semantics, no
hard-coded colours outside `V5Colors`/`V5Gradients`.

## 5. Screens (M12)

Each row lists the module, the copy (verbatim; `{x}` = bound value) and the behaviour. Pixel values are in §1's lines.

### Onboarding (`:feature:onboarding`)
- **Welcome (M12-04).** `welcome` gradient + grain, white text. "Stay Focused" (16/600) · "Take back\nyour time." (62/300)
  · "Block the apps that eat your day. Free and open source." (18/500) · `White` button "Get started" → sign up ·
  text link "This is my child's phone" (underlined) → the shipped kids path.
- **Sign up (M12-05).** Back button · "Create your\naccount" · "So you can manage your child's phone and keep your blocks
  when you switch phones." · `White` "Continue with Google" (blue bold "G") · divider "or with email" · fields "Email",
  "Password (6+ characters)" · errors "Enter a valid email address." / "Password needs at least 6 characters." ·
  `Primary` "Continue" · "Already have an account? **Sign in**" (switches to the shipped sign-in mode).
- **Permissions (M12-06).** "Four switches,\nthen you're set" · "Android needs these to block apps. Your data stays on
  this phone." · 4 progress segments (green when granted, hairline when not) · rows (white r28, padding 16):
  "Usage access" / "See how long each app is used"; "Accessibility" / "Spot a blocked app the moment it opens";
  "Display over other apps" / "Show the block screen on top"; "Notifications" / "Tell you when a block starts or ends".
  Each row: `Primary` 44 dp "Allow", or a 44 dp green circle with a check. Bottom button "{n} of 4 allowed" (opacity .35)
  → "Start using Stay Focused" when all 4 are on; then toast "All set, {first name}" (no name: "All set").
  Accessibility "Allow" opens the **disclosure sheet**: "Accessibility access" · card: **What it reads** "Which app is on
  screen and the address of websites you open." · **Why** "To block an app or site the moment it opens." · **Never**
  "Your messages, passwords or typing. Nothing leaves this phone." · `Primary` "Agree and open Settings" · text
  "No thanks". (Play policy: keep the shipped consent logging/flow; only the presentation changes.)

### Home (`:feature:home`, M12-07, M12-08)
- Header: 46 dp white avatar circle with the first letter of the user's name (tap → Account, shipped behaviour) · date
  "EEEE, d MMMM" (13/500 secondary) · "Hi, {first name}" (21/500; signed out: "Hi") · bell `RoundIconButton` → child
  screen of the first linked phone needing attention, else Family; red badge when any linked phone has an open request
  or alert. Hide the bell when no phone is linked.
- **Hero** (`violet`, r38): "Screen time today" · `DotMatrixText` h:mm (s = 8) · goal note "{n} min under your 3h goal" /
  "{n} min over your 3h goal" (goal from M12-01; format the goal with the shared duration formatter) · `DotGrid` of
  today's 24 hours (rows 6, max 20 min, current hour highlighted) · two `GlassTile`s → Insights: "Blocked" + count
  (s = 5) + "times" + "Apps stopped today"; "Most used" + minutes + "min" + app name (icon = app's letter/icon).
- Tiles row: **Block apps** (`surfaceRaised`, white "+" → wizard for this phone; two tilted app tiles = the apps of the
  first two active blocks; footer card "Active blocks" / "{n} on this phone") and **Take a break** (`breakTile`):
  idle = "Take a\nbreak" + "+" + `BreakArt` (last length) → sheet; running = "On a\nbreak" + `DotMatrixText` mm:ss +
  "Tap to end early" → ends it, toast "Break ended".
- AI pill (white, h62): orange sparkle · "Describe a block in your words" · ink arrow circle → Describe with AI.
- Child card per linked phone (`pinkCard`): name · `OnlinePill` "Online" (offline: hide the pulse, text "Offline") ·
  "Today" h:mm · "Using now" app · "Battery" n% · request row "Wants {n} more min on {app}" + "Review" → child screen.
  No linked phone: white row "Link a child's phone" + pink "+" circle → scan (or the shipped sign-in gate).
- **Take a break sheet:** "Take a break" · "Every distracting app locks. Calls and messages still work." · value
  (88/200) + unit · `TickRuler` 5–120 step 5 (passed orange, current ink) · presets 15 min / 30 min / 1h / 1h 30m
  (selected ink) · `Orange` "Start {30 min} break" → toast "Break started: {30 min}". Durations use `fm()`:
  `<60 → "N min"`, whole hours "Nh", else "Nh Mm".

### Blocks (`:feature:block`, M12-09 … M12-12)
- **Tab:** "Blocks" (38/300) · `SegmentedPill` "My phone" / each linked phone name · orange card: "New block" (28/400)
  + "on {target}" + white "+" → wizard; glass row with sparkle "Or describe it in words" → AI · "{on} of {n} blocks on" /
  "No blocks yet" · block cards (white r32): `AppIconStack`, title (first two app names joined " + ", then " +{n}"),
  type label (Daily limit / Use, then lock / Blocked during hours / Blocked now), `V5Toggle` (toast "Block paused" /
  "Block is on"), rule sentence (22/300): limit "{45 min} a day"; cycle "{10} min open, then {30 min} locked";
  schedule "{22:00}–{07:00}, {days}"; now "Until {HH:mm}"; limit/cycle add `TickProgress` + "{31 min} used today" /
  "{6} of {10} min used" and the max. Paused cards at opacity .6. FABs above the tab bar (right 18): white 58 dp AI
  sparkle + orange "New block" pill with orange shadow. Tapping a card keeps the shipped edit flow (extra).
  `days`: "every day", "Mon–Fri", "weekends", else "Mon, Tue, …".
- **Wizard header:** back (step − 1, or close on step 1) · close · "{step title}" (30/400) + red target tag
  "{My phone | phone name}" (tap flips target; no linked phone → toast "Link a child's phone first") ·
  "Step {n} of 3 · tap the red tag to switch phone". Titles: "What should it do?", "Which apps?", "Set the rule".
  AI draft banner: tag "AI draft" + "Check the details, then turn it on."
- **Step 1:** 2 × 2 type tiles (178 dp, r32, type gradient): "Time limit" / "A daily allowance, then it locks";
  "Use, then lock" / "Open for a bit, then a cooldown"; "Block during hours" / "School, work or bedtime";
  "Block now" / "Lock them right away". Selected = 3 dp background gap + 3 dp ink ring and a filled white dot.
  `Primary` "Continue" · text "Rather describe it in words" (orange sparkle).
- **Step 2:** 3-column grid of the launchable apps (catalog from M3-02), tiles 112 dp r28, 48 dp icon, name 14/500;
  selected = 2 dp inner ink ring + ink check badge. Button "Continue with {n} app(s)" / "Pick at least one app"
  (opacity .35, disabled).
- **Step 3:** rule card (type gradient, r36): cycle → glass `SegmentedPill` "Use for {10}m" / "Then lock {30 min}"
  editing one ruler; ruler types show the value (92/200) + unit + sub ("allowed per day", "open before it locks",
  "locked, then it opens again", "blocked from now") and the ruler with "{min}", "Drag to set", "{max}". Ranges:
  limit 5–240 step 5; use 1–60 step 1; rest 5–180 step 5; now 15–480 step 15. Schedule → "Blocked from" +
  "{from}–{to}" (58/200) + 2 × 2 presets Work 09:00–17:00, School 08:00–15:00, Night 22:00–07:00, Dinner 19:00–20:30
  (Work/School also set Mon–Fri). "Repeat on" `DayChips` (hidden for Block now). "Summary" card:
  limit "{apps} can be used {45 min} a day, {days}. After that they lock until midnight."; cycle "Open {apps} for
  {10} min, then they lock for {30 min}. This repeats {days}."; schedule "{apps} are blocked from {from} to {to},
  {days}."; now "{apps} are blocked for the next {1h}." where {apps} = "A and B", or "A, B and {n} more", or
  "These apps". `Primary` "Turn on block" → toast "Block is on" or "Block sent to {phone}", then back to the Blocks tab
  (or the child screen for a child target).
- **Describe with AI:** back · target tag · "Describe a block" · "AI sets it up. Nothing turns on until you check it." ·
  white card with a 150 dp multi-line field (21/300), placeholder "e.g. Let me use Instagram for 10 minutes, then lock it
  for 30" · example chips "Instagram 10 min, then lock it for 30", "No TikTok or YouTube on school days 8–3",
  "YouTube max 1 hour a day", "Block Reddit for the next 2 hours" · `Orange` "Build my block" (sparkle; disabled at .4
  when empty) → busy: spinner + "Reading your request" → step 3 with the AI draft banner. Uses the shipped AI pipeline
  (Gemini via Firebase AI Logic, on-device fallback).

### Blocked app screen (`:core:blocking`, M12-13)
`blocked` gradient, white. 76 dp app icon (r26, shadow) · "{App} is resting" (34/300) · "Opens again in" ·
`DotMatrixText` mm:ss (s = 9) · rule line (cycle) "Your rule: {10} min open, then {30} min locked. You used your {10}
minutes." · `White` "Close {App}" → home screen. Other block types keep their shipped reason text (extra, M13-06).

### Family & linking (`:feature:devices`, M12-14 … M12-16)
- **Family tab** ("Devices" is renamed): "Family" + "Phones you manage" · per phone a white card r34: name (20/500),
  "{model} · Android {version}", online pill, "Today" / "Using now" / "Battery", `HourTicks`, attention line (red dot,
  `#C21F26`): "1 request waiting" and/or "Protection is off" joined by " · " → child screen · `pinkLink` tile
  (236 dp): "Link a\nchild's phone" + "+" + `LinkArt` → scan · "Install Stay Focused Kids on their phone. It shows a
  code, and you scan it from here."
- **Child's phone:** header back · name (22/500) · "{model} · {74}% battery" · online pill · `pinkHero`: "Screen time
  today", `DotMatrixText`, "Using {app} now", `DotGrid` of the child's hours · alert card: tag "Protection off" + "{4 min
  ago}" + "Accessibility was turned off on {phone}. Blocks are paused until it's back on." + `Primary` "Send reminder"
  (toast "Reminder sent to {name}") / `Secondary` "Dismiss" · request card: tag "Request" + "{Just now}" +
  "{name} wants {30} more minutes on {app}." + 15m / 30m / 1h (ink) / No (track) → toasts "{name} got {30 min} more" /
  "Request declined" · tiles: ink "Lock all apps" / "Calls still work" ↔ "Unlock apps" / "Everything is locked now"
  (toasts "All apps locked on {name}" / "Apps unlocked"); orange "Focus 25 min" / "Start a study session" ↔
  "Focus is on" / "{mm:ss} left · tap to stop" (toasts "Focus started on {name}" / "Focus stopped") · "Blocks on this
  phone" + ink "New block" pill → wizard for this phone · compact block rows (r28) with toggles · text button
  "Unlink this phone" (`#C21F26`) → shipped unlink flow, toast "Phone unlinked".
  {name}: the child-facing name. Until a child name exists, use the phone name (open question 2).
- **Scan:** `scan` gradient, white. Glass back · "Scan their code" (30/300) · "Open Stay Focused Kids on your child's
  phone and point your camera at the code." · 260 dp camera viewport with 4 rounded corner brackets (44 dp, 3 dp, r22)
  and the scan line · "Looking for a code…". Camera via the shipped CameraX + ML Kit scanner.
- **Codes match:** back · "Do the codes\nmatch?" · "Your child's phone shows the same six digits." · white card with the
  code as `DotMatrixText` ("482 913" format, s = 5) · "Name this phone" + field · "Your child sees that you manage this
  phone and can unlink anytime. You get notified if they do." · `Primary` "Codes match, link it" → toast "{name} is
  linked". The shipped waiting/declined/expired states stay (extra).

### Insights (`:feature:insights`, M12-17)
"Insights" + `SegmentedPill` Day / Week. Violet card: "Screen time today" / "Daily average this week", total as
"{2}h {33}m" (66/200, units 22/300 white .8), `GlassBarChart` (day: 24 hours, current hour highlighted, avg = total/24
shown "Avg {6}m/h"; week: last 7 days with today highlighted, avg shown "Avg {2h 52m}"), axis labels
"12 AM · 6 AM · 12 PM · 6 PM · 11 PM" or "M T W T F S S" (start from the right weekday). Tiles: orange "Unlocks" +
`DotMatrixText` + "Today"; violet "Blocked" + count + "Times today". White list: top 6 apps by minutes, `UsageRow`,
bar width relative to the top app.

### Kids app (`:kids`, M12-18)
- **Show code:** `kidsQr` gradient · "Stay Focused Kids" · "Show this to\nyour parent" · "They scan it from their phone." ·
  white r38 card with the QR (ZXing, ink modules, ~210 dp) · "Code expires in {4:52}". The prototype's
  "Parent scanned it (demo)" button is **not** shipped.
- **Allow parent:** "Stay Focused Kids" (pink text) · "Let {parent} manage\nthis phone?" · card "Check that {parent}'s
  phone shows" + code · card "{parent} will be able to" + pink bullets: "See screen time and which app is open",
  "Block apps and set time limits", "Lock the phone for study time", "Answer your requests for more time" ·
  "You can unlink anytime from this app. {parent} gets a notification if you do." · `Primary` "Allow and link" ·
  text "Not now". Toast "Linked to {parent}".

## 6. New data v5 needs (M12-01 … M12-03)
| v5 element | Missing today | Story |
|---|---|---|
| Home "{n} min under your 3h goal" | No screen-time goal | M12-01: DataStore `screenTimeGoalMins`, default 180, read-only in v5 |
| Home "Most used" tile, Blocked count | Check `HomeModel`; add if missing | M12-07 |
| Family "Android {version}", child hourly dots/ticks | Status report has model, battery, app, today total only | M12-02: `osVersion` in the status doc; today's 24 hourly buckets in the daily usage doc, next to `totalMins` (rules + tests) |
| "Lock all apps" tile | No command | M12-03: `LOCK_ALL` / `UNLOCK_ALL` commands, child enforcement (calls, SMS, launcher, settings and Stay Focused stay open), parent state |
| Insights Week | 7-day cache exists (M3-03) | M12-17 |

## 7. Extras: shipped, not in v5 (epic M13)
| Area | Extra | Story |
|---|---|---|
| Onboarding | "Continue without an account", "Continue for now" (skip permissions), forgot password / reset, restricted-settings guidance, battery-optimisation guidance | M13-01 |
| Account | Account screen, permission health, open-source links, privacy policy, sign out, delete account sheet | M13-02 |
| Blocks | All apps tab with quick lock, templates, block edit/delete, hourly limits, custom time range picker, block names | M13-03 |
| Insights | Day stepper (past days), Opens metric/tab | M13-04 |
| Devices | Link sign-in gate, camera permission states, link waiting/declined/expired/network errors, offline / long-offline alerts, per-app lock toggles, focus 50 / 90 min | M13-05 |
| Kids | Kids home, kids permissions + disclosure, blocked overlay "Ask for more time" (15/30/60 → "Request sent"), unlinked state, blocked reasons for non-cycle types | M13-06 |
| Home | Shipped Home items v5 doesn't show (e.g. average marker, launches/unlocks stats, per-block toggles) | M13-08 |
| Cleanup | Remove v2 tokens/components/fonts, move `core.ui.v5` → final package, light root theme, docs | M13-07 |

M13 stories have no v5 mock-ups: use v5 components and patterns, keep shipped copy, and put screenshots in the PR for
design sign-off.

## 8. Open questions (defaults used until answered)
1. **Goal editing.** v5 shows "3h goal" but no way to change it. Default: fixed 3 h; an editor is a later story.
2. **Child name.** v5 uses "Aarav" (child) and "Aarav's phone" (device). The data has only the device name. Default:
   use the device name everywhere.
3. **Contrast.** White 13–15 sp text on `#FE5E0E` / `#E8336F` is about 3:1 (below WCAG AA for small text). Default: keep the
   design; M12-19 reports the spots for a design decision.
4. **Dark mode.** v5 is light only. Default: light only (as v2 was dark only).
