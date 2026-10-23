# v5 visual QA, accessibility and performance review (M12-19)

Scope: the 24 v5 screens of epic M12. Reference = `design/screens-v5/NN-*.png` (390x844 frame, status bar and tab bar
drawn by the prototype). Render = the committed Roborazzi baseline (`src/test/screenshots/*.png`, taken without a device
frame, so status bar and device corners are never part of the comparison).

Decisions: **accepted** (known and fine), **extra** (a v5 extra from `docs/design/V5_SPEC.md` section 2 that the reference
does not draw), **fixed** (fixed in this PR), **follow-up** (issue to open).

Method: references and renders were compared by eye. "Viewed" marks the pairs inspected side by side while writing this
review; the others were checked against the per-screen PR bodies (each M12 story already compared its own render with its
reference) and the spec copy. Pixel diffing is not possible: sizes, density and sample data differ by design.

## 1. Screen by screen

Common differences that apply to every row and are accepted: no status bar, device corners or prototype tab bar in the
renders; sample data differ (the render uses fixture data, e.g. `HomeTypicalDay` shows "Y"/"A" tiles where the PNG shows
"I"/"T"); the render width is 360 or 411 dp instead of 390.

| # | Reference | Roborazzi render | Viewed | Differences and decision |
|---|---|---|---|---|
| 01 | `01-welcome.png` | `feature/onboarding/src/test/screenshots/Welcome.png` | yes | Same copy, gradient and button. Render is taller (no frame). Accepted. |
| 02 | `02-sign-up.png` | `feature/onboarding/src/test/screenshots/SignUp.png` | no | Copy and layout per M12-05 PR. Accepted. |
| 03 | `03-permissions.png` | `feature/onboarding/src/test/screenshots/Permissions.png` (+ `PermissionsAllSet.png`) | no | Per M12-06 PR. Accepted. |
| 04 | `04-accessibility-disclosure.png` | `feature/onboarding/src/test/screenshots/` has no dedicated baseline (sheet is covered by the instrumented/Compose test of M12-06) | no | **Follow-up F1**: add a Roborazzi baseline for the disclosure sheet so the 24th screen has a committed render. |
| 05 | `05-home.png` | `feature/home/src/test/screenshots/HomeTypicalDay.png` | yes | Hero, dot grid, tiles identical in structure. Extras below the tiles (AI pill, child card, average card, block toggles) are not in the reference: **extra** (M13-08). Sample tiles differ: accepted. |
| 06 | `06-take-a-break-sheet.png` | `feature/home/src/test/screenshots/BreakSheet.png` | no | Per M12-08 PR. Accepted. |
| 07 | `07-break-running.png` | `feature/home/src/test/screenshots/HomeBreakRunning.png` | no | Per M12-08 PR. Accepted. |
| 08 | `08-blocks-my-phone.png` | `feature/block/src/test/screenshots/BlockWithBlocks.png` | no | "All apps" tab, templates, hourly limits are **extras** (M13-03). Accepted. |
| 09 | `09-blocks-child-phone.png` | `feature/block/src/test/screenshots/BlockWithDevice.png` | no | Accepted. |
| 10 | `10-new-block-1-type.png` | `feature/block/src/test/screenshots/WizardType.png` | no | Accepted. |
| 11 | `11-new-block-2-apps.png` | `feature/block/src/test/screenshots/WizardApps.png` | no | Accepted. |
| 12 | `12-new-block-3-use-then-lock.png` | `feature/block/src/test/screenshots/WizardRulesCycle.png` | yes | Identical structure and copy. The render is clipped after the Summary card because the screenshot height is fixed, so the "Turn on block" button is outside the baseline: accepted (it is covered by `new_block_wizard.yaml`). Summary wraps "10 / min" differently at 360 dp: accepted. |
| 13 | `13-new-block-3-schedule.png` | `feature/block/src/test/screenshots/WizardRulesSchedule.png` | no | Custom ranges are an **extra** (M13-03). Accepted. |
| 14 | `14-describe-with-ai.png` | `feature/block/src/test/screenshots/DescribeEmpty.png` | no | Accepted. Inline error line is an **extra**. |
| 15 | `15-ai-draft-review.png` | `feature/block/src/test/screenshots/DescribeFilled.png` and `WizardRulesCycle.png` with the AI banner | no | Accepted. |
| 16 | `16-blocked-app-screen.png` | `core/blocking/src/test/screenshots/BlockLimitDaily.png`, `BlockCycleRest.png`, `BlockSchedule.png` | yes | Same icon, headline, dot countdown and white button. Reference shows a `14:41` MM:SS countdown and the rule sentence ("Your rule: 10 min open, then 30 min locked. You used your 10 minutes."), the limit variant renders "5:12:09" and "Today's limit is used up.": different rule type, accepted. The cycle variant carries the rule sentence. |
| 17 | `17-family.png` | `feature/devices/src/test/screenshots/DevicesOnline.png` | yes | The "THIS PHONE · PIXEL 8 · PARENT · 1 LINKED" chip is an **extra** (M13-05). The reference's "1 request waiting · Protection is off" line is missing in this fixture (no alert in `DevicesOnline`; shown in `DevicesAlert.png`). Accepted. |
| 18 | `18-child-s-phone.png` | `feature/devices/src/test/screenshots/RemoteOnline.png` (+ `RemoteLocks.png`, `RemoteAlert.png`) | no | Remote lock and requests are **extras** (M12-03, M13-05). Accepted. |
| 19 | `19-scan-code.png` | `feature/devices/src/test/screenshots/LinkScan.png` | no | Camera preview is blank in Robolectric: accepted. |
| 20 | `20-codes-match.png` | `feature/devices/src/test/screenshots/LinkConfirm.png` | no | Accepted. |
| 21 | `21-insights-day.png` | `feature/insights/src/test/screenshots/InsightsDay.png` | yes | Date navigator (arrows, "Today / 08 OCT") and the Screen time / Opens switch above the app list are **extras** (M13-04). Bars and tiles match; bar heights differ (fixture). Accepted. |
| 22 | `22-insights-week.png` | `feature/insights/src/test/screenshots/InsightsWeek.png` | no | Accepted. |
| 23 | `23-show-code.png` | `kids/src/test/screenshots/KidsShowCode.png` | no | Accepted. |
| 24 | `24-allow-parent.png` | `kids/src/test/screenshots/KidsAllowParent.png` | no | Accepted. |

Nothing was fixed in code in this PR: every difference seen is a documented extra, fixture data or the missing baseline
(F1). Follow-ups F1 and F2 are listed in the PR body; they are to be added to `backlog/phase1/` by a maintainer.

## 2. Contrast report (V5_SPEC section 8, question 3)

WCAG 2.x relative luminance, computed from the token values in `V5Colors.kt`. AA needs 4.5:1 for normal text, 3:1 for
large text (>= 18 sp, or >= 14 sp bold) and for UI components.

| Pair | Ratio | AA normal | AA large |
|---|---|---|---|
| white on `orange` `#FE5E0E` (Welcome, blocked screen, "Unlocks" tile, CTA backgrounds) | **3.08** | fail | pass |
| white on `orangeGlowDeep` `#FF7A2E` | 2.60 | fail | fail |
| white on `orangeSoft` `#F7934F` ("Take a break" tile) | 2.27 | fail | fail |
| white on `orangeGold` `#FEBE42` (Welcome glow, bottom right) | 1.66 | fail | fail |
| white on `pink` `#E8336F` | 4.09 | fail (just) | pass |
| white on `pinkGlow` `#EC5A8A` | 3.28 | fail | pass |
| white on `pinkTint` `#EC6A93` (child card, link tile) | 2.98 | fail | fail (just) |
| white on `pinkRose` `#E77A9E` | 2.74 | fail | fail |
| white on `violet` `#4B3FD1` (Home/Insights hero) | 7.17 | pass | pass |
| white on `violetGlow` `#9B8CFF` (hero glow centre) | 2.77 | fail | fail |
| white on `green` `#2F9A3C` (rule card) | 3.62 | fail | pass |
| white on `red` `#E5242B` ("My phone" tag) | 4.55 | pass | pass |
| ink `#000B1A` on `orange` | 6.42 | pass | pass |
| ink on `orangeSoft` | 8.70 | pass | pass |
| ink on `pink` | 4.83 | pass | pass |
| `textSecondary` `#5B6472` on background `#EDEDEB` | 5.10 | pass | pass |
| `textSecondary` on white | 5.98 | pass | pass |
| `pinkText` `#C21F59` on white | 5.78 | pass | pass |
| `onlineText` `#1F7A33` on `onlineBackground` | 4.80 | pass | pass |
| `onInkMuted` on ink | 12.38 | pass | pass |

Findings:
1. White 13-15 sp text on `orange` (3.08), the pink card family (2.7-4.1), the "Take a break" tile (2.27) and the orange
   gold glow (1.66) is below AA for normal text. Large text (headlines, dot countdowns, 18 sp+) passes on `orange` and
   `pink` only.
2. The worst spots are the Welcome subtitle and "This is my child's phone" link where the gradient reaches `orangeGold`
   (1.66), the "Take a break" tile caption (2.27) and the child-card labels ("Today", "Using now", "Battery" at 60%
   white on `pinkTint`).
3. Dark text (`ink`) passes AA on every orange and pink surface.

**Decision requested from a maintainer** (not made here, it changes the design): keep the design as is (default in the
spec), or (a) darken the text-bearing orange to about `#D94A00` (white 4.5:1), (b) switch small text on orange/pink/gold
surfaces to `ink`, or (c) keep white only for text of 18 sp and up. Option (b) needs no new colour. Until the decision,
no colour or text-colour code was changed.

## 3. Accessibility

Done in this PR: contrast report above. Static check: every interactive element in the v5 screens carries a role
(`Role.Button`, `Checkbox`, `Switch`) and a content description where it is icon-only (see the `semantics` blocks in
`AppRow.kt`, `WizardScreen.kt`).

Not done here, tracked in `docs/qa/pending-manual-checks.md` (needs a device or emulator with TalkBack):
- TalkBack walk-through of all 24 screens.
- Font scale 1.3 and 2.0 on every screen (clipping, overlapping, unreachable buttons).
Findings from those walks are fixed in the PR that finds them or filed as issues.

## 4. Performance

Neither Macrobenchmark nor JankStats is set up in the repo yet, and the development machine cannot run an x86 emulator
(see the M1-16 entry in the pending checks). Adding a benchmark module is out of this story's scope. No measurement was
taken: the frame-time check for Home and Insights is a pending manual check (JankStats log or `adb shell dumpsys
gfxinfo com.dculus.stayfocused framestats` on a mid-range emulator, 5 scrolls of Home and 5 Day/Week toggles on Insights;
record janky-frame percentage here). Follow-up F2: add a Macrobenchmark module with a Home and an Insights scenario.

## 5. E2E flows (Maestro)

`e2e/flows/journeys/` (listed in `e2e/flows/config.yaml`, run by the `e2e` workflow, not by the PR smoke gate to keep the
gate under 5 minutes):

| Journey | Flow |
|---|---|
| Onboarding to Home | `journeys/onboarding_to_home.yaml` |
| New block via the wizard | `journeys/new_block_wizard.yaml` |
| Describe with AI (on-device fallback, CI has no Firebase config) | `journeys/describe_with_ai.yaml` |
| Break start and end | `journeys/break_start_end.yaml` (smoke has `smoke/take_a_break.yaml`) |
| Insights Day and Week | `journeys/insights_day_week.yaml` |

Existing smoke flows (`smoke/*`) are unchanged and still match the v5 copy.
