# Prompt for Claude Code: restyle the app from v5 to v6 (green)

Copy `Stay Focused v6.dc.html` into the repo's `design/` folder next to `Stay Focused v5.dc.html`, then paste everything below the line into Claude Code.

---

The Stay Focused design moved from **v5** to **v6**. Update the Android app (Kotlin + Jetpack Compose) to match v6. This is a visual restyle plus a few layout changes. Flows, copy (except one line, below), state and logic are unchanged.

## Sources
- `design/Stay Focused v6.dc.html`: the new source of truth. Read its template for exact values.
- `design/Stay Focused v5.dc.html`: the previous version. Diff the two files yourself to confirm the list below and catch anything I missed.
- `design/screens-v5/` and `design/Stay Focused - All Screens.html` are now outdated for colour and type. Use them only for layout and flow.

## 1. Theme tokens (`ui/theme/`)

**Typography**
- Replace Urbanist with **Plus Jakarta Sans** everywhere, weights 200–800. Use Google downloadable fonts or bundle the TTFs.
- Keep the existing size, weight and letter-spacing scale. Big numbers use weight 200.

**Colours**

| Role | v5 | v6 |
|---|---|---|
| Ink: text, primary buttons, selected chips, toggle track on, segmented track, toast | `#000B1A` | `#163D22` (deep forest) |
| Tab bar background | `rgba(0,11,26,.92)` | `rgba(22,61,34,.92)` |
| Shadows and scrims | `rgba(0,11,26,a)` | `rgba(22,61,34,a)` (same alpha) |
| Secondary text | `#5B6472` | `#56604F` |
| Panel label text | `#3A4250` | `#38432F` |
| Inactive text on dark (tabs, segments) | `#C9CDD3` | `#C5D6BA` |
| Accent: icons, progress ticks, rank bars, FAB, AI draft and Request tags, break ruler fill, kid usage ticks | `#FE5E0E` | `#2F9A3C` |
| Links | `#FE5E0E` | `#2F9A3C` |

- Remove every orange (`#FE5E0E`, `#FF9A3C`, `#FF7A2E`, `#FEBE42`, `#F7934F`), pink (`#E8336F` family) and violet (`#4B3FD1`, `#9B8CFF`) token.
- Keep the semantic reds as they are: `#E5242B`, `#C21F26`, `#D12A2A`, used for alerts, the notification dot, Unlink and form errors.
- Keep the app brand colours on the app icon tiles: Instagram, YouTube and the rest.

**One brand gradient, `BrandGradient`**
- CSS: `radial-gradient(80% 70% at 75% 15%, #9CCB4A 0%, transparent 70%), #2F9A3C`.
- In Compose: a solid `#2F9A3C` fill, with an elliptical radial gradient `#9CCB4A → transparent` drawn on top. Centre it at (75% width, 15% height). Its radii are 80% of width × 70% of height, and it fades out by 70%.
- Keep the existing grain overlay on gradient cards.
- This is the **only** gradient in the app. Text on it is always white.
- Full-screen variants:
  - **Welcome** and the **Blocked app overlay**: base `#2F9A3C`, plus radial `#5DB548` and radial `#B9DE6A` (Welcome) or `#9CCB4A` (Blocked) highlights in the same positions as v5.
  - **Kids "Show this to your parent"**: radial `#9CCB4A` at 50% 45% over `#2F9A3C`.

**Soft status pill, `SoftPill`**
- Background `#E8F5EA`, text `#1F7A33` 13sp semibold, height 30dp, horizontal padding 12dp.
- Leading 7dp dot `#2F9A3C`. For Online, use the existing pulsing dot instead.
- Use it for **Online** status and for the **phone target tag** ("My phone" / "{child}'s phone").

## 2. Components
- **Numerals:** delete the dot-matrix numeral renderer. Every number uses plain Plus Jakarta Sans at weight 200. This covers the Home screen-time total, the Blocked and Most used stats, the Take a break card, the break countdown, Unlocks, the pairing code, the Blocked overlay countdown and the child's screen time.
- **`SmoothAreaChart`:** replaces the dotted 24×6 dot grid.
  - Points are evenly spaced. Join them with cubic Béziers whose control points sit at the horizontal midpoint between neighbours: `C(mx, y0) (mx, y1) (x1, y1)`.
  - Stroke: white, 2.4dp, round caps and joins.
  - Fill: a vertical gradient from white at 55% alpha to 0.
  - Height: 72dp. The top 10dp is headroom, so max value = height − 14dp.
  - Mark the current point with a 10dp white dot and a 5dp halo of white at 28%.
  - Axis labels sit underneath at 11sp semibold, 85% opacity: "12 AM, 6 AM, 12 PM, 6 PM, 11 PM", or "M T W T F S S" for the week view.
  - It takes values, max, highlight index and labels as parameters.
- **Primary buttons:** solid `#163D22` with white text. This includes **"Build my block"** and **"Start {x} break"**, which were gradients in v5.

## 3. Screen changes
- **Home**
  - The screen-time hero uses `BrandGradient` (it was violet), with `SmoothAreaChart` (24 hourly points, current hour highlighted) in place of the dot grid.
  - The **Take a break** card uses `BrandGradient` (it was orange). Its centre tick stays `#D8F23C`.
  - The **child phone** card uses `BrandGradient` (it was pink), with white text. Its Online pill stays white at 22%.
  - The "Describe a block" row's sparkle is `#2F9A3C`, and its arrow button is `#163D22`.
- **Blocks tab**
  - The **New block** card is now a **white card**, not a gradient: radius 32dp, padding 18dp, soft shadow.
    - On the left, the title "New block" (26sp regular, `#163D22`), with a `SoftPill` underneath reading "on {target}".
    - On the right, a 56dp `#163D22` circle holding a white +.
  - **"Or describe it in words"** has moved out of that card. It is now its own white row underneath: 62dp tall, radius 31dp. Left to right: a `#2F9A3C` sparkle, the text in `#56604F`, then a 46dp `#163D22` circle with a white arrow. It is styled the same as the Home describe row.
  - The floating "New block" button is solid `#2F9A3C`.
- **New block, step 1 (type tiles)**
  - All four tiles are **white** with `#163D22` text.
  - The **selected** tile fills with `BrandGradient` and white text. Drop the outline ring and use the shadow `0 14 28 -14 rgba(47,154,60,.6)`.
  - The radio dot's border uses the tile's text colour, and the dot is filled white when its tile is selected.
- **New block, step 3**
  - The rule card **always** uses `BrandGradient`, whatever the rule type.
  - The target tag is now a `SoftPill` (it was a red pill).
  - **Copy change:** "Step 3 of 3 · tap the red tag to switch phone" becomes "… tap the **green** tag to switch phone".
- **Describe with AI:** the target tag is a `SoftPill`, and the **Build my block** button is solid `#163D22`.
- **Insights**
  - The hero uses `BrandGradient`.
  - **Replace the bar chart** with `SmoothAreaChart`:
    - Day view: 24 hourly points, current hour highlighted.
    - Week view: 7 daily points labelled M–S, today highlighted.
  - Remove the bars, the dashed average line and the floating Avg pill.
  - The average now sits in a small chip on the right of the "Screen time today" label row: white at 22%, 26dp tall, 12sp bold, reading "Avg 6m/h" (or the weekly average).
  - The **Unlocks** and **Blocked** cards both use `BrandGradient` with white text.
  - The app ranking progress bars are `#2F9A3C`.
- **Family:** the "Link a child's phone" card uses `BrandGradient`, and the kid usage ticks are `#2F9A3C`.
- **Child's phone (remote)**
  - The hero uses `BrandGradient`, with `SmoothAreaChart` for the child's day (current hour highlighted).
  - The Lock card is solid `#163D22`, and the Focus card uses `BrandGradient` with white text and icon.
  - The Request tag is `#2F9A3C`.
- **Scan:** keep the dark `#0D0F12` background, with a glow of `rgba(47,154,60,.4)`. The scan line is a gradient through `#C8E88F` with a `#2F9A3C` glow.
- **Kids consent:** the "Stay Focused Kids" label is `#23772F`, and the bullet dots are `#2F9A3C`.
- **Take a break sheet:** the ruler fill is `#2F9A3C`, the current tick is `#163D22`, the selected preset chip is `#163D22`, and the Start button is solid `#163D22`.

## 4. Process
1. Update `Color.kt`, `Type.kt` and the gradient/brush helpers first. Add `BrandGradient`, `SoftPill` and `SmoothAreaChart` to the component gallery, then stop for my review.
2. Then update the screens in this order: Home, Blocks, the New block wizard, Describe with AI, Insights, Family and Child's phone, then onboarding, the Blocked overlay and Kids. Use one commit per area.
3. After each area, compare against `Stay Focused v6.dc.html` and list any differences.
4. Remove the dead code: the dot-matrix renderer, the dot grid, the insights bar chart, and the old orange, pink and violet tokens. Don't leave unused colours behind.
5. Don't change behaviour, copy (apart from the one line above), navigation or data.
