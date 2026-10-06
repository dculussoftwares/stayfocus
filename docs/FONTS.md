# Fonts

| Role | Design font | Licence | In the repo? | Bundled fallback |
|---|---|---|---|---|
| Display | Clash Display 600 | ITF Free Font License | No | Space Grotesk (OFL) |
| Body | Satoshi 400/500/700/800 | ITF Free Font License | No | Plus Jakarta Sans (OFL) |
| Labels, numbers | Geist Mono 500/600/700 | SIL OFL 1.1 | Yes | n/a |

## Licence finding (M1-04)

Clash Display and Satoshi are Fontshare "closed source" fonts under the
[ITF Free Font License](https://www.fontshare.com/licenses/itf-ffl) (see also the
[Fontshare FAQ](https://www.fontshare.com/faq)). They are free to use in apps and products, including
commercially, but the licence **does not allow redistributing the font files** themselves. Committing them to a
public GPL-3.0 repository would be redistribution, so **they are not in the repo**. Only Fontshare's "open source"
fonts are OFL and redistributable; these two are not. Other public repos that committed ITF fonts were asked to remove
them, e.g. [kollektiv-mc/Kollektiv#40](https://github.com/kollektiv-mc/Kollektiv/issues/40).

The licence page is rendered with JavaScript and could not be read automatically in full, so a maintainer should read it
once and confirm this summary. Embedding the fonts in a released APK/AAB is ordinary app use under the licence; that is a
release-pipeline decision for a later story.

## What the build does

- The OFL substitutes and Geist Mono are bundled in `core/ui/src/main/res/font/` (variable fonts). Their licences are in
  `docs/licenses/`.
- `scripts/dev/fetch-fonts.sh` downloads Clash Display and Satoshi into the ignored
  `core/ui/src/main/assets/fonts/`. At runtime `loadFontFamilies()` uses them when all files are present and
  otherwise falls back to the substitutes. The project builds and tests (including screenshots) without them.
- Screenshot baselines are recorded with the substitutes, so CI is deterministic.
