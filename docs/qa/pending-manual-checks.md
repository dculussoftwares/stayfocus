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

## M2-03 Block screen
- Check: the block screen appears within 500 ms when a blocked app opens, on API 26 and API 36 (attach a screen recording to the PR); it cannot be dismissed into the blocked app with back, recents or home + reopen; fill the "Verified" column of `docs/blocking/block-screen-launch.md` for API 29, 34, 35, 36 (activity or overlay path).
- How: install `:app`, enable the accessibility service, and (until the engine story wires `show`) call `BlockScreenLauncher.show(pkg, Decision.Block(...))` from a debug hook while opening that app. Record with `adb shell screenrecord`.

## M1-15 Security scanning
- Check: after the first apply, secret scanning, push protection and Dependabot alerts are enabled and the next plan shows no drift; a PR adding a dependency with a known critical CVE fails dependency review; a PR adding a public Firestore rule or an over-privileged IAM binding is flagged by Trivy or CodeRabbit.
- How: Settings > Code security on GitHub; then try the two cases on a throwaway PR (needs the live repository and the M1-12 App permissions).

## M6-07 Web privacy policy and account deletion
- Check: both pages are live at the Pages URL and account deletion works from the web on dev.
- How: after merge, `infra-firebase` applies, then `pages` deploys (Actions). Open `https://dculussoftwares.github.io/stayfocus/privacy.html` and `delete-account.html`. Add `dculussoftwares.github.io` to Auth authorised domains if sign-in is rejected. With a throwaway dev account (needs M6-06's `deleteAccount` deployed): sign in with Google and with email, delete, and confirm the Auth user and `users/{uid}` are gone. The URLs are then wired into the app (M6-05) and `docs/play/` (M10-01).
