# Play store listings

Draft listing copy for both apps, plus the checklist for the graphics a person must produce from real builds.
Status: **draft, awaiting human review** (M10-05). Do not paste into the Play Console until the review checklist below is ticked.

| File | App |
|---|---|
| `stay-focused.md` | Stay Focused (`:app`) |
| `stay-focused-kids.md` | Stay Focused Kids (`:kids`) |

Play limits: title 30 characters, short description 80, full description 4000.

## Graphics (a person produces these from real builds)

Store the files next to this README. Never use mock-ups that show features that are not shipped.

| Asset | Spec | Stay Focused | Kids |
|---|---|---|---|
| App icon | 512 x 512 PNG, 32-bit, max 1 MB | `icon-app.png` | `icon-kids.png` |
| Feature graphic | 1024 x 500 PNG or JPEG, no alpha | `feature-app.png` | `feature-kids.png` |
| Phone screenshots | 2 to 8, 16:9 or 9:16, 320 to 3840 px per side | `screenshots/app-*.png` | `screenshots/kids-*.png` |

Suggested screenshots, taken from a release-like build with the final copy:
- Stay Focused: Home (gauge and hourly bars), Block wizard, AI draft (marked "AI DRAFT"), Devices, Remote device screen.
- Kids: Link screen (QR), Home with the "This phone is managed by" notice, the disclosure screens for Usage Access and Accessibility.

## Review checklist (human, acceptance criterion of M10-05)

- [ ] Stalkerware policy: the Kids listing says the app is for parents managing a child's phone, that the child sees a
      persistent notice and must consent on the phone, and never uses "spy", "hidden", "secret" or "track without them knowing".
- [ ] Neither listing promotes covert use; there is no wording about hiding the app icon or avoiding detection.
- [ ] Accessibility and Usage Access are disclosed in the description as they are in the in-app prominent disclosure.
- [ ] AI-generated content policy: the AI feature is described as drafting a block from the user's sentence, the user
      confirms before anything turns on, and "Report a bad result" exists in the app.
- [ ] Families Policy answers and target audience for Kids match `docs/play/DATA_SAFETY.md`.
- [ ] Every feature mentioned is shipped in the build being submitted (remove the rest).
- [ ] Graphics come from real builds and contain no third-party trademarks or unreleased features.
