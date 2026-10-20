# Pending manual checks

Acceptance criteria that need a device, an emulator image or a person. Tick them off and delete the entry once done.

| Story | Check | How |
|---|---|---|
| M1-01 | Both apps install side by side and launch on API 26 and API 36 emulators | `./gradlew assembleDebug`, then `adb install app/build/outputs/apk/debug/app-debug.apk` and `adb install kids/build/outputs/apk/debug/kids-debug.apk` on each emulator and open both: each shows its placeholder text. Automated by Gradle Managed Devices in M1-16 |
| M1-16 | `instrumented-tests`, `e2e-smoke` and `e2e.yml` run green on GitHub-hosted runners, the first run being the PR itself; `e2e.yml` publishes video and screenshots | Not runnable on the Apple-silicon dev machine (x86_64 emulator images, no KVM). Check the PR's CI run, then trigger `e2e` via *Actions → e2e → Run workflow* and open the `e2e-results` artifact. Also confirm `ci-pass` fails when a smoke flow is broken (temporarily change an `assertVisible` on a throwaway PR) |
| M2-02 | Foreground transitions are correct (launcher → app → recents → another app → notification shade) on API 26 and API 36; paste the log in the PR | Install `:app`, enable the "Stay Focused" accessibility service in Settings, run `adb logcat` while switching apps, and watch `ForegroundAppTracker.foreground` (temporary log line). Expect: shade and keyboard do not change the app; screen off clears it |
| M6-02 | A debug build sends an App Check token | Needs a real Firebase project (M6-01). Build `:app` with a real `app/google-services.json`, register the debug token (Logcat tag `DebugAppCheckProvider`) in the Firebase console, then make a Firebase request and confirm verified requests in the App Check metrics |
| M1-12 | After the first apply: a direct push to `main` is rejected; a PR can't merge with an unresolved thread or red `ci-pass`; the next plan shows no drift | Needs *Repository → Administration* on the `stayfocus-automation` App (docs/INFRASTRUCTURE.md, bootstrap step 3). Try it on a throwaway PR and branch |
| M11-01 | `V5Screen` sets status/navigation-bar icon colour on API 36 (light and dark content), and Urbanist weights 200-700 render on device | Robolectric covers API 26 and 34 only (SDK 36 needs JDK 21, CI uses 17). On an API 36 emulator or phone, show `V5Showcase` inside `V5Screen(darkContent = true/false)` from a debug hook and check the bar icons and the "Weights" section |
| M12-03 | Lock all apps on a real child phone: apps are blocked, calls still work, unlock restores everything | Two phones on dev, linked as in M7-07, Kids phone with accessibility, usage and overlay permissions granted. The parent tile arrives with M12-15, so until then create `commands/{id}` docs by hand (read-only console otherwise, or the emulator) with `type: LOCK_ALL`, `payload: {}`, `status: pending`, `createdAt` = server time. (1) After `LOCK_ALL` the device doc shows `allLocked: true` and opening any app (YouTube, Chrome, Camera) on the Kids phone shows the block screen "Everything is locked now". (2) The dialer, Messages, the launcher, Settings and Stay Focused Kids still open; place and receive a call. (3) Reboot the Kids phone (and force-stop the app) while locked: it is still locked once the accessibility service is back. (4) `UNLOCK_ALL`: `allLocked` is false and apps open again. (5) Unlink while locked: the Kids phone stops enforcing the lock |

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

## M9-04 Gemini client via Firebase AI Logic
- Check: (1) the live eval reports accuracy on the shared fixture; (2) on a debug build with Firebase configured and an App Check debug token registered, "Describe it" returns a Gemini-built rule, and with airplane mode on it still returns a rule from the on-device parser; a build without `google-services.json` also works.
- How: (1) `GEMINI_API_KEY=<key> ./gradlew :core:sync:testDebugUnitTest --tests '*GeminiEvalTest*' -i` and read the `EVAL ACCURACY` line (the key is a personal Gemini Developer API key; never commit it). Run it nightly or before changing the prompt or model. (2) The UI that calls `AiBlockBuilder.build` lands with M9-05; until then exercise it from a debug build or test. The fake-model unit tests cover timeout, malformed JSON, errors and the fallback.

## M4-04 Edit, toggle and delete blocks
- Check: on a device, tap a block row on the Block tab: the wizard opens at step 3 with its values and "Save changes"; saving keeps the row's position (same `createdAt`) and its on/off state. "Delete block" opens the bottom sheet ("Delete this block?" / "Delete" / "Keep it"); "Delete" removes the row and the app is no longer blocked by it. Toggling a block on the Block tab is reflected on Home and vice versa.
- How: debug build; create a block, edit and delete it. A Maestro flow for this journey is a follow-up (needs the emulator pipeline).

## M7-06 Link a child's phone: QR scanner
- Check: (1) with the M7-05 QR on a second phone, in normal indoor light, the scanner reads it in under 2 s and opens "Confirm link"; (2) the first visit shows the camera rationale, "Allow camera" asks, and denying shows "Camera is off" with "Open Settings" (turning the camera on there and coming back starts the preview); (3) scanning a non-Stay-Focused QR (e.g. a URL) shows the "That isn't a Stay Focused code" toast and keeps scanning; (4) signed out, "Link a child's phone" shows the sign-in prompt.
- How: a physical device (camera) plus a second phone or screen showing a `stayfocused://link?t=...` QR. Note the time-to-scan in the PR. The status bar above the scanner is the app background (#0E0F0D), not pure black: check it is acceptable.

## M6-06 Account deletion

- Check: on dev, with a signed-in parent account that has a linked child phone: Account, "Delete account and all data", confirm. (1) With a sign-in older than 5 minutes the sheet first asks for the password (or Google) again; a wrong password shows an inline error and nothing is deleted. (2) After "Delete account and all data" the toast "Account and all data deleted." shows, the app returns to Welcome and a restart still shows Welcome. (3) In the Firebase console (read-only) `users/{uid}`, its `devices`, `fcmTokens`, claimed `linkTokens` and the Auth user are gone. (4) The child phone notices its device document is gone and resets (M7-09). (5) Signing in again with the same email creates a fresh empty account.
- How: dev build with `google-services.json`. Prerequisite: re-run `infra-bootstrap` (manual) so the deploy identity may grant `roles/firebaseauth.admin` (`deploy_iam_grantable_roles` in `infra/bootstrap/variables.tf`) before the `deleteAccount` function can be applied.

## M7-08 Kids monitoring service and notification
- Check: with a parent name stored (the link flow, M7-07, will set it and call `MonitoringLauncher.sync()`): (1) "Managed by {parent}'s phone" shows with the shield icon; (2) swiping it away (Android 14+) brings it back within a few seconds; (3) after a reboot it reappears without opening the app, on Android 14 and on Android 15+; (4) the merged `:app` manifest has no monitoring service or `isMonitoringTool` flag.
- How: a physical device or emulator on API 34 and 35+; `adb reboot`, then `adb shell dumpsys notification | grep -i "managed by"`. Also confirm Play Console accepts the `isMonitoringTool` meta-data declaration when the Kids app is first uploaded (Play help vs. Android docs differ: meta-data `child_monitoring` vs. attribute `parental_control`).

## M7-05 Kids: QR with one-time token

- Check: on a real Kids phone with Firebase configured, the Link screen shows the QR and "WAITING FOR SCAN · 4:5x" counting down; a stock camera app reads it as the text `stayfocused://link?t=...`; the M7-06 scanner opens "Confirm link"; after 5 minutes unscanned a new QR appears (new token); airplane mode shows "Could not get a code..." with "Try again".
- How: dev build with `google-services.json`, second phone or the stock camera app. Note: the Kids app does not register the `stayfocused://` scheme, so the camera app only shows the text.

## M8-02 Child status reporter

- Check: with a linked Kids phone (the link flow, M7-07, must store the parent uid and device id via `ReportTargetStore`): (1) `users/{uid}/devices/{id}` shows battery, charging, currentApp (a label), `online: true` and a fresh `lastSeen` about every 90 s, and sooner (about 5 s) after switching app or plugging in; (2) `usage/{today}` appears and refreshes at most every 15 min; (3) `apps` lists the launchable apps and follows an install or uninstall within seconds; (4) stopping the service writes `online: false`; (5) the Firestore usage graph shows about 1 status write per 90 s at steady state; (6) battery cost over 2 h: `adb shell dumpsys batterystats --reset`, use the phone normally for 2 h, then `adb shell dumpsys batterystats com.dculus.stayfocused.kids` (or Battery Historian) and note the percentage in the PR/issue.
- How: dev build with `google-services.json` on a physical device.

## M7-07 Pairing handshake: claim, 6-digit code, consent

- Check: two real phones on dev (parent app signed in with a non-anonymous account, Kids app), both with Firebase configured. (1) Kids Link shows the QR; the parent scans it and "Do the codes match?" shows the same 6 digits (grouped `482 913`) as the Kids "Let {name} manage this phone?" screen. (2) Editing "Name this phone" and then tapping "Allow and link" on the Kids phone creates the device with that name (give the edit a second before tapping Allow). (3) "Allow and link": the parent toasts "Phone linked" and lands on the Remote device screen; the Kids phone goes to Home and shows the "Managed by {name}'s phone" notification. (4) "Cancel" on Kids: the parent shows "Not linked" and the Kids phone shows a new QR. (5) Do not tap anything for 5 minutes: the parent shows "That code ran out". (6) Scan the same QR with a second parent account after the first claimed it: "That code was already used". (7) Airplane mode on the parent while scanning: "No connection" and "Try again" works afterwards. Record the screen of both phones for the PR.
- How: dev build with `google-services.json` and App Check debug tokens registered for both apps; re-run `infra-bootstrap` (manual, with its Apply input enabled; the default is plan-only) first so the deploy identity may grant `roles/firebaseappcheck.tokenVerifier` (the two functions consume App Check tokens; `deploy_iam_grantable_roles` in `infra/bootstrap/variables.tf`); then deploy `claimLinkToken` and `approveLink` through the `infra-firebase` workflow (merge of this PR). Record with `adb shell screenrecord` on each phone.

## M8-01 Command pipeline: latency

- Check: with a linked Kids phone and a parent device that can write commands (until the M8-03 UI lands, add a debug call to `CommandSender.send(target, RemoteCommand.SyncBlocks)` or create `commands/{id}` docs by hand with status `pending` and a server `createdAt`), send 20 commands with the Kids screen on and 20 with the screen off (locked, idle 5+ min). Latency is the time from `createdAt` to `ackAt` in each doc; report p50 and p95 for both cases in the PR/issue. Also: (1) a command sent with the Kids phone in airplane mode is applied (status `done`/`failed`) soon after it is back online, and the parent shows "Phone is offline, it'll apply when it's back" after 10 s; (2) a pending doc with `createdAt` older than 24 h becomes `expired` and is not run; (3) after a reboot and after force-stopping the app, the next command still arrives (the listener restarts with the service). Handlers arrive in M8-03/M8-05; until then a command ends `failed` (no handler), which still proves the round trip.
- How: dev build with `google-services.json`, physical Kids phone; read `createdAt` and `ackAt` in the Firebase console (read-only) or the emulator.

## M7-09 Kids home and unlink

- Check: two real phones on dev, linked as in M7-07. (1) Kids Home shows the "This phone is managed by {name}" notice card, "Today" with a total that matches Android's Digital Wellbeing within a few minutes, and "Rules set by {name}" listing the enabled blocks the parent made for this phone (a new block appears without reopening the app). (2) Child direction: "Unlink from {name}" asks first; "Unlink" shows the toast "Unlinked. {name} has been notified.", the Kids app returns to the QR (Link) screen, the "Managed by" notification disappears, and the device (with blocks, usage and apps) is gone from Firestore; the parent gets no push yet (M8-05 sends it; the function logs `notify-pending`). (3) Parent direction: relink, then delete the device in the parent app once M8-04 lands (or run the `unlinkDevice` callable as the parent); the Kids phone drops to the QR screen within seconds with no toast. (4) Relink, then delete the parent's account (Account, Delete account): the Kids phone resets to the QR screen. (5) Airplane mode on the Kids phone, tap "Unlink": "Could not reach the server..." and the link stays; retry online works. (6) Kill and reopen the Kids app while linked: it opens on Home, not on the QR.
- How: dev build with `google-services.json` and App Check debug tokens; the `unlinkDevice` function is deployed by the `infra-firebase` workflow when this PR merges. Record the screens of both phones.

## M8-04 Parent: remote device screen

- Check: a parent phone and a linked Kids phone on dev. On the parent's Remote screen: (1) header shows ONLINE, battery and "USING {APP}" matching the Kids phone; (2) turn the lock toggle on for an app: the toast says "{app} locked on {device}" and the Kids phone blocks it within seconds (needs M8-03); off unlocks it; (3) Focus 25/50/90 starts a session on the Kids phone, the card shows a countdown, Stop ends it; (4) a block's toggle switches it on the Kids phone; "New block for {name}" opens the wizard targeting that phone; (5) revoke accessibility on the Kids phone: the red card appears (needs M8-06), Remind shows a notification on the Kids phone, Dismiss clears it; (6) Unlink this phone removes it from Devices and the Kids phone returns to the QR screen; (7) airplane mode on the Kids phone: a toggle toasts "{device} is offline. It'll apply when it's back." after 10 s. The unlock request card's buttons only render until M8-05. Record the screen.
- How: dev build with `google-services.json` and App Check debug tokens; `adb shell screenrecord` on both phones.

## M8-03 Child enforcement of parent blocks, locks and focus

- Check: two real phones on dev, linked as in M7-07, Kids phone with the accessibility, usage and overlay permissions granted. (1) In the parent's block wizard the child's pill is on; choose it, pick apps from the child's list (they come from the Kids phone) and tap "Turn on block": the Kids phone blocks one of those apps within a few seconds. (2) Switch the block off and on in the parent's Block tab: enforcement follows on the Kids phone. (3) Put the Kids phone in airplane mode after step 1: the block is still enforced, also after a reboot of the Kids phone. (4) Create a second block while the Kids phone is in airplane mode, then turn the network back on: it is enforced within one sync. (5) `START_FOCUS`/`STOP_FOCUS`/`BLOCK_APP`/`UNBLOCK_APP`/`SYNC_BLOCKS` (create `commands/{id}` docs by hand with status `pending` until the Remote screen lands in M8-04): focus blocks every app except the always-allowed ones until the end and `focusEndsAt` appears on the device doc; the lock applies and lifts. (6) Unlink: the Kids phone stops enforcing everything.
- How: dev build with `google-services.json` and App Check debug tokens; read the device doc and commands in the Firebase console (read-only) or the emulator. Record both screens.

## M8-05 Unlock requests + notifyParent push function

- Check: a parent phone (signed in, notifications allowed) and a linked Kids phone on dev, with the infra-firebase workflow having deployed `notifyParent` (merge of this PR). (1) Lock an app for the child from Remote, open it on the Kids phone: the block screen shows "Ask {parent} for more time"; tap it, pick 15m / 30m / 1h: it shows "Request sent to {parent}". (2) The parent's phone gets a notification "{device}: {n} more minutes of {app}?" within seconds, and the Remote screen shows the amber card (and Devices a badge). (3) Answer from the notification with "Approve 15m": the notification disappears, the Kids phone opens the app without the block screen within 10 s, and the block comes back when the 15 minutes are over. (4) Repeat answering from the amber card (15m / 30m / 1h / NO): the toast reads "Approved {15m} of {app}" or "Request denied", and NO leaves the app blocked. (5) Sign out of the parent app, make a request: no notification arrives; sign in again: it arrives for the next request. (6) Unlink from the Kids phone: the parent gets a notification "{device} unlinked". (7) Airplane mode on the parent while answering: the answer applies once it is back online. Time steps 1 to 3 from the tap to the unlocked app (target < 10 s) and record both screens.
- How: dev build with `google-services.json` and App Check debug tokens on both apps; `adb shell screenrecord` on both phones. `notifyParent` and its Eventarc trigger are created by the infra-firebase workflow on merge (Terraform); read its logs in Cloud Logging (`fn=notifyParent`, outcome `sent=… pruned=…`).

## M10-05 Store listings for both apps

- Check: a person (1) reviews `docs/play/listing/stay-focused.md` and `stay-focused-kids.md` against Google Play stalkerware and AI-Generated Content policies and ticks the checklist in `docs/play/listing/README.md`; (2) produces the icon, feature graphic and phone screenshots from real builds as listed there and commits them to `docs/play/listing/`; (3) checks the claims against the shipped build (remove anything not shipped, such as installed apps, battery level, "Report a bad result").
- How: build both apps, take screenshots with `adb exec-out screencap -p`, design the feature graphics, then open a follow-up PR with the files. The issue stays open until this is done.

## M8-06 Tamper alerts
- Check: turning Accessibility off on a linked Kids phone shows "Accessibility turned off on {name}" on the parent's phone within 2 min (push and the red card on Remote); Remind posts a notification on the child that opens Accessibility settings; Dismiss clears the card; turning it off again after turning it on raises a new alert; a phone left off for 24 h shows "Offline for a while" and "Last seen ...".
- How: needs a real Firebase dev project with `notifyParentAlert` deployed (infra-firebase apply on merge), a parent phone and a Kids phone linked. Turn off Accessibility on the Kids phone and time the push. The 24 h case can be checked by editing `lastSeen` of the device document in the emulator or console (read only otherwise).

## M10-08 Device QA matrix
- Check: the full Phase 1 regression in `docs/qa/phase1-matrix.md` run on Pixel (API 36), Samsung, Xiaomi (HyperOS), Oppo or Vivo and an API 26 device; every failure filed as an issue; no open blocker issues remain. The issue stays open until this is done.
- How: follow "How to run" in `docs/qa/phase1-matrix.md` with a release-candidate build (`docs/RELEASING.md`) and a second phone for the Kids app; fill in the tables in a follow-up PR.

## M11-05 TickRuler TalkBack
- Check: with TalkBack on, focusing a TickRuler (wizard rule card, break sheet) announces its name and value (e.g. "45 minutes"); swipe up/down (or volume keys while focused) changes the value by one step without dragging.
- How: once a screen uses TickRuler (M11-0x / M12-1x), run a debug build on a phone with TalkBack, focus the ruler and adjust it; the semantics (`setProgress`, range info) are already covered by `TickRulerBehaviourTest`.
