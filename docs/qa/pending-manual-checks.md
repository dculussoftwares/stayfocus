# Pending manual checks

Acceptance criteria that need a device, an emulator image or a person. Tick them off and delete the entry once done.

| Story | Check | How |
|---|---|---|
| M1-01 | Both apps install side by side and launch on API 26 and API 36 emulators | `./gradlew assembleDebug`, then `adb install app/build/outputs/apk/debug/app-debug.apk` and `adb install kids/build/outputs/apk/debug/kids-debug.apk` on each emulator and open both: each shows its placeholder text. Automated by Gradle Managed Devices in M1-16 |
| M1-16 | `instrumented-tests`, `e2e-smoke` and `e2e.yml` run green on GitHub-hosted runners, the first run being the PR itself; `e2e.yml` publishes video and screenshots | Not runnable on the Apple-silicon dev machine (x86_64 emulator images, no KVM). Check the PR's CI run, then trigger `e2e` via *Actions → e2e → Run workflow* and open the `e2e-results` artifact. Also confirm `ci-pass` fails when a smoke flow is broken (temporarily change an `assertVisible` on a throwaway PR) |
| M2-02 | Foreground transitions are correct (launcher → app → recents → another app → notification shade) on API 26 and API 36; paste the log in the PR | Install `:app`, enable the "Stay Focused" accessibility service in Settings, run `adb logcat` while switching apps, and watch `ForegroundAppTracker.foreground` (temporary log line). Expect: shade and keyboard do not change the app; screen off clears it |
| M6-02 | A debug build sends an App Check token | Needs a real Firebase project (M6-01). Build `:app` with a real `app/google-services.json`, register the debug token (Logcat tag `DebugAppCheckProvider`) in the Firebase console, then make a Firebase request and confirm verified requests in the App Check metrics |
| M1-12 | After the first apply: a direct push to `main` is rejected; a PR can't merge with an unresolved thread or red `ci-pass`; the next plan shows no drift | Needs *Repository → Administration* on the `stayfocus-automation` App (docs/INFRASTRUCTURE.md, bootstrap step 3). Try it on a throwaway PR and branch |

## M3-01 UsageStats data source
- Check: on a real device, today's total from `UsageStatsDataSource.dayUsage(today)` is within ±5% of Digital Wellbeing (screenshot comparison).
- How: grant Usage access, use the phone for a while, compare the total with Settings > Digital Wellbeing for the same day. Wired to UI in M3-04; until then log `dayUsage(LocalDate.now()).totalMins`.

## M10-03 Release pipeline
- Check: a pushed tag produces signed AABs that install via bundletool on a device (R8 build starts and runs both apps).
- How: after the `release` environment secrets exist (docs/RELEASING.md), push a `v0.x.y-rc1` tag, download the AABs from the GitHub Release, `bundletool build-apks` + `install-apks`, open each app.

## M6-01 Firebase Auth and apps
- Check: the first apply on `main` creates both Firebase Android apps and enables email/password and anonymous sign-in; the next plan shows no changes. A Firebase-enabled CI build (`build-with-terraform-firebase-config`) downloads its config from the Terraform output, and an emulator test account can sign in.
- How: first re-run `infra-bootstrap` with a temporary key (new roles, docs/INFRASTRUCTURE.md "Firebase Auth and apps"), then merge. The sign-in test itself lands with the first Auth code (M6-03 onwards); until then check in the Firebase console (read only) that the providers are on.

## M2-03 Block screen
- Check: the block screen appears within 500 ms when a blocked app opens, on API 26 and API 36 (attach a screen recording to the PR); it cannot be dismissed into the blocked app with back, recents or home + reopen; fill the "Verified" column of `docs/blocking/block-screen-launch.md` for API 29, 34, 35, 36 (activity or overlay path).
- How: install `:app`, enable the accessibility service, and (until the engine story wires `show`) call `BlockScreenLauncher.show(pkg, Decision.Block(...))` from a debug hook while opening that app. Record with `adb shell screenrecord`.

## M1-15 Security scanning
- Check: after the first apply, secret scanning, push protection and Dependabot alerts are enabled and the next plan shows no drift; a PR adding a dependency with a known critical CVE fails dependency review; a PR adding a public Firestore rule or an over-privileged IAM binding is flagged by Trivy or CodeRabbit.
- How: Settings > Code security on GitHub; then try the two cases on a throwaway PR (needs the live repository and the M1-12 App permissions).

## M6-07 Web privacy policy and account deletion
- Check: both pages are live at the Pages URL and account deletion works from the web on dev.
- How: after merge, `infra-firebase` applies, then run the `pages` workflow by hand (Actions, Run workflow). Open `https://dculussoftwares.github.io/stayfocus/privacy.html` and `delete-account.html`. If Google sign-in is rejected because the Pages domain is not an authorised Auth domain, leave this check pending and add the domain through Terraform (follow-up on the Identity Platform config), not a console. With a throwaway dev account (needs M6-06's `deleteAccount` deployed): use one throwaway account per method (Google, then email; create a new one after the first is deleted): sign in, delete, and confirm the Auth user and `users/{uid}` are gone. The URLs are then wired into the app (M6-05) and `docs/play/` (M10-01).

## M1-09 Navigation shell
- Check: Maestro flow `e2e/flows/smoke/navigation_tabs.yaml` passes on an emulator (onboarding placeholders, tabs, back to Home, Account keeps the tab bar). The Robolectric tests in `:app` already cover tab state, back behaviour and tab-bar visibility.
- How: install `:app` on an emulator, then `maestro test e2e/flows/smoke/navigation_tabs.yaml`. Automated by M1-16.

## M5-03 Permissions repository and settings intents
- Check: on API 26, 33 and 36 devices, each permission row state updates after returning from its settings screen; each intent opens the right screen (usage, accessibility, overlay (this app's own screen on API 26; the app list on 30+ including 33 and 36, where you pick Stay Focused), notifications, battery list); the restricted-settings guidance condition holds for a sideloaded install on 33+.
- How: needs the onboarding UI (M5 stories) or a debug hook calling `PermissionsRepository.observe()` and `PermissionIntents`; on an API 33+ device install the APK by opening it from a browser download or file manager (adb installs are exempt from Android's restricted settings, so they do not exercise the real case) and confirm the guidance condition is true and the Accessibility toggle is greyed until "Allow restricted settings"; compare with a Play internal-testing install, where the condition is false.

## M7-01 Firestore and App Check
- Check: after merge and apply, App Check enforcement is active on dev Firestore, and a request without a valid token is rejected by App Check (not merely by the rules).
- How: needs the maintainer bootstrap step (roles datastore.owner/viewer) first. (1) Confirm the mode with `curl -H "Authorization: Bearer $(gcloud auth print-access-token)" https://firebaseappcheck.googleapis.com/v1/projects/<project>/services/firestore.googleapis.com` and expect `"enforcementMode": "ENFORCED"`. (2) Because the baseline rules deny everything, a 403 alone proves nothing: compare the error text of a header-free Firestore REST call with the Firebase console's App Check metrics for Cloud Firestore ("unverified requests" rises), or re-check once M7-03 rules allow a test path (valid token succeeds, missing token fails).

## M7-02 Cloud Functions scaffold
- Check: after the first apply on `main`, `ping` is deployed, a call without an App Check token or sign-in is refused, a call from a debug build succeeds, and a later PR changing only another function leaves `ping` untouched in the plan.
- How: first re-run `infra-bootstrap` with a temporary key (new deploy roles for Cloud Functions, Cloud Run, Storage and a conditional `projectIamAdmin`, docs/INFRASTRUCTURE.md "Cloud Functions"), then merge. Check the plan comment for `allUsers` on the Cloud Run services (an organisation policy may block it). Call `ping` with `curl` (expect 401) and from a debug build of `:app` once M7-07 adds a client.

## M2-06 Daily and hourly limits
- Check: on a device with usage access and the accessibility service on, a 2-minute daily limit on Chrome blocks after about 2 minutes of use (within 10 s) and stays blocked until midnight; an hourly limit unblocks at the next clock-hour boundary.
- How: create a daily limit block (2 min) with Chrome, open Chrome and keep it open; note the time to the block screen. Reopen Chrome: still blocked. Change the device clock past midnight (or wait): unblocked. Repeat with an hourly limit near the top of an hour. The unit tests already cover the UsageStats + live-delta merge and the hour/day resets.

## M2-05 Block now and Take a break
- Check: on a real phone with Accessibility on, a 5-minute break blocks a non-allowlisted app and leaves the dialer usable; after expiry the app opens normally and a "Break over" notification appears (channel Breaks, only with the notification permission). Killing the app process mid-break leaves the countdown correct on reopening. Maestro flow `e2e/flows/smoke/take_a_break.yaml` passes on an emulator.
- How: dial 5 min in the sheet (or 15 min preset), open Instagram or similar, dial a number, wait for the end, then `adb shell am kill com.dculus.stayfocused` during a second break and reopen. The engine, break-then-allowlist and "Break over" logic are covered by `BlockingEngineTest` and `AndroidBreakNotifierTest`; the flow is also run by CI job `e2e-smoke`.
## M4-02 Block wizard host, steps 1 and 2
- Check: on an emulator or phone, Block tab > New block opens the wizard; tapping a type card goes to the app grid (real launcher icons and today's time); Continue with 0 apps shows "Pick at least one app"; system Back steps back one step and closes from step 1; "Rather just describe it?" returns to the Block tab with the Describe panel open (needs AI enabled in settings). The Robolectric tests cover the flow with fakes.
- How: install `:app`, open Block > New block. A Maestro flow can be added once step 3 and the save (M4-03 to M4-05) exist.

## M2-08 Schedules
- Check: a schedule starting 1 minute from now blocks the already-open app when it starts; changing the device time or time zone re-evaluates immediately.
- How: create a "Block during hours" block starting in 1 minute for a non-allowlisted app, keep the app open and watch for the block screen at the start time. Then, with the app open, move the device time across a schedule boundary (Settings > Date & time, automatic time off) and confirm blocking changes immediately; finally switch the time zone and confirm the block follows local time. Overnight (22:00-07:00) and day-chip logic is covered by RuleEvaluatorTest; the engine timer by BlockingEngineTest.

## M9-03 Firebase AI Logic and App Check
- Check: after apply on dev, a Gemini request through Firebase AI Logic without an App Check token is rejected, and one from a debug build with a registered token succeeds.
- How: needs the maintainer bootstrap step (roles serviceusage.apiKeysAdmin/apiKeysViewer). Confirm with `curl -H "Authorization: Bearer $(gcloud auth print-access-token)" https://firebaseappcheck.googleapis.com/v1/projects/<project>/services/firebaseml.googleapis.com` and expect `"enforcementMode": "ENFORCED"`. Then, once M9-04 has a client, call the model from a debug build with no App Check debug token registered and confirm the failure is an App Check error and that the Firebase console's App Check metrics for Firebase AI Logic show unverified requests (a bare curl can be rejected by the API key restriction first, so it does not prove App Check).

## M4-03 Wizard step 3 (rules, summary, save)
- Check: on an emulator or phone, create each of the four block types through Block > New block (dial drag and presets, Per day/Per hour tabs, USE FOR / THEN LOCKED tabs, schedule presets and the Custom time picker, day chips). "Turn on block" returns to the Block tab with that target selected, shows the toast "{name} is on · {target}" and the new block is in the list. The custom picker works with TalkBack and the 24 h dials look right in the dark theme.
- How: install `:app`, run through the four types. The summary sentences, saved fields and the create-and-find-in-list journey are covered by `BlockSummaryTest`, `WizardRulesViewModelTest` and `CreateBlockFlowTest` (Robolectric). A Maestro flow is a follow-up because it needs an emulator run.

## M2-09 Engine robustness
- Check: fill in the device columns of `docs/qa/blocking-matrix.md` (Pixel API 36, API 26 emulator, Samsung, Xiaomi); every FAIL needs a fixed bug or a filed issue.
- How: follow each row (reboot, `adb shell am kill`, force-stop, split-screen, picture-in-picture, app updates, language change) with an active block, cycle and break. Unit tests cover the health monitor and engine restart; the platform rows need a device.

## M6-03 Email and password auth: session survives a restart
- Check: sign in with email on a debug build that has a Firebase configuration, force-stop the app (Settings, Apps, Force stop), relaunch: the account is still signed in (`AuthRepository.currentUser` emits the user). Also tap "Forgot password" with a real mailbox and confirm the reset email arrives.
- How: needs the dev `google-services.json` (see `docs/INFRASTRUCTURE.md`) and a throwaway account; a person on a device or emulator. The Firebase SDK persists the session, so this is expected to pass.

## M6-04 Google sign-in: account picker with two accounts
- Check: on a device with 2 Google accounts, tap "Continue with Google": the account picker lists both; choosing one signs in and continues; closing the picker shows no error; with no Google account on the device the "No Google account found" line shows; airplane mode shows the "No connection" line.
- How: needs the dev `google-services.json` (with the web client ID and the debug SHA-1 registered, see `docs/INFRASTRUCTURE.md`) and a physical device or Google Play emulator with two accounts. Record the result in the PR.

## M5-04 System check screen
- Check: on a real device, each Allow row opens the right system screen and the row flips to On live on return; Accessibility goes through the disclosure first ("No thanks" leaves it as Allow); notifications ask once, then open the app's notification settings; "Continue for now" with fewer than 4 shows "Blocking needs all 4. Finish in Account." on Home.
- How: install a debug build, revoke permissions, walk the onboarding; repeat on API 33+ (runtime notification dialog) and below 33.
