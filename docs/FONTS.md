# Fonts

| Role | Font | Licence | In the repo? |
|---|---|---|---|
| All text (display, body, labels, numbers) | Plus Jakarta Sans 200-800 (variable `wght`) | SIL OFL 1.1 | Yes, `core/ui/src/main/res/font/plus_jakarta_sans.ttf` |

The design system (`com.dculus.stayfocused.core.ui`, spec in [`docs/design/V5_SPEC.md`](design/V5_SPEC.md)) uses a single
family, **Plus Jakarta Sans** by Tokotype, from [Google Fonts](https://fonts.google.com/specimen/Plus+Jakarta+Sans) (SIL OFL 1.1, so it can
live in this repo). It is bundled as the variable font `core/ui/src/main/res/font/plus_jakarta_sans.ttf` and loaded at weights 200,
300, 400, 500, 600, 700 and 800 by `PlusJakartaSansFontFamily`. Nothing is downloaded at runtime and there is no fallback, so
screenshots and devices render the same. The licence is `docs/licenses/PlusJakartaSans-OFL.txt`.

## History

v5 used Urbanist; v6 (green) replaced it with Plus Jakarta Sans.

The v2 design used Clash Display and Satoshi (Fontshare, ITF Free Font License, which does not allow redistributing the
font files) with Space Grotesk and Plus Jakarta Sans (OFL) as bundled stand-ins, plus Geist Mono. All of them, the
`scripts/dev/fetch-fonts.sh` download script and their licence texts were removed together with the v2 design system
(M13-07).
