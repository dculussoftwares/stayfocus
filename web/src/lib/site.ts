// Links and facts used across the site. Keep store ids in sync with applicationId in app/ and kids/build.gradle.kts.
export const PLAY_VESPER = "https://play.google.com/store/apps/details?id=com.dculus.vesper";
export const PLAY_KIDS = "https://play.google.com/store/apps/details?id=com.dculus.vesper.kids";
export const SOURCE = "https://github.com/dculussoftwares/vesper";
export const ISSUES = "https://github.com/dculussoftwares/vesper/issues";
// minSdk 26 (build-logic ProjectExtensions.kt).
export const MIN_ANDROID = "Android 8.0";

export interface Faq {
  q: string;
  a: string;
}
