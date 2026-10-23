# Fonts

| Role | Font | Licence | In the repo? |
|---|---|---|---|
| All text (display, body, labels, numbers) | Urbanist 200-700 (variable `wght` 100-900) | SIL OFL 1.1 | Yes, `core/ui/src/main/res/font/urbanist.ttf` |

The design system (`com.dculus.stayfocused.core.ui`, spec in [`docs/design/V5_SPEC.md`](design/V5_SPEC.md)) uses a single
family, **Urbanist** by Corey Hu, from [Google Fonts](https://fonts.google.com/specimen/Urbanist) (SIL OFL 1.1, so it can
live in this repo). It is bundled as the variable font `core/ui/src/main/res/font/urbanist.ttf` and loaded at weights 200,
300, 400, 500, 600 and 700 by `UrbanistFontFamily`. Nothing is downloaded at runtime and there is no fallback, so
screenshots and devices render the same. The licence is `docs/licenses/Urbanist-OFL.txt`.

## History

The v2 design used Clash Display and Satoshi (Fontshare, ITF Free Font License, which does not allow redistributing the
font files) with Space Grotesk and Plus Jakarta Sans (OFL) as bundled stand-ins, plus Geist Mono. All of them, the
`scripts/dev/fetch-fonts.sh` download script and their licence texts were removed together with the v2 design system
(M13-07).
