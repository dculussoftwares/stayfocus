# Pending manual checks

Acceptance criteria that need a device, an emulator image or a person. Tick them off and delete the entry once done.

| Story | Check | How |
|---|---|---|
| M1-01 | Both apps install side by side and launch on API 26 and API 36 emulators | `./gradlew assembleDebug`, then `adb install app/build/outputs/apk/debug/app-debug.apk` and `adb install kids/build/outputs/apk/debug/kids-debug.apk` on each emulator and open both: each shows its placeholder text. Automated by Gradle Managed Devices in M1-16 |
| M2-02 | Foreground transitions are correct (launcher → app → recents → another app → notification shade) on API 26 and API 36; paste the log in the PR | Install `:app`, enable the "Stay Focused" accessibility service in Settings, run `adb logcat` while switching apps, and watch `ForegroundAppTracker.foreground` (temporary log line). Expect: shade and keyboard do not change the app; screen off clears it |
| M6-02 | A debug build sends an App Check token | Needs a real Firebase project (M6-01). Build `:app` with a real `app/google-services.json`, register the debug token (Logcat tag `DebugAppCheckProvider`) in the Firebase console, then make a Firebase request and confirm verified requests in the App Check metrics |
| M1-12 | After the first apply: a direct push to `main` is rejected; a PR can't merge with an unresolved thread or red `ci-pass`; the next plan shows no drift | Needs *Repository → Administration* on the `stayfocus-automation` App (docs/INFRASTRUCTURE.md, bootstrap step 3). Try it on a throwaway PR and branch |

## M3-01 UsageStats data source
- Check: on a real device, today's total from `UsageStatsDataSource.dayUsage(today)` is within ±5% of Digital Wellbeing (screenshot comparison).
- How: grant Usage access, use the phone for a while, compare the total with Settings > Digital Wellbeing for the same day. Wired to UI in M3-04; until then log `dayUsage(LocalDate.now()).totalMins`.

## M10-03 Release pipeline
- Check: a pushed tag produces signed AABs that install via bundletool on a device (R8 build starts and runs both apps).
- How: after the `release` environment secrets exist (docs/RELEASING.md), push a `v0.x.y-rc1` tag, download the AABs from the GitHub Release, `bundletool build-apks` + `install-apks`, open each app.
