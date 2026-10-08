# Play Console: Data safety answers

Answers for the Play Console "Data safety" form, one section per app. Reviewed against the Firestore schema
(`docs/IMPLEMENTATION_PLAN.md` section 7) and the SDKs the apps include (Firebase Auth, Cloud Firestore, Cloud
Messaging, App Check with Play Integrity, AI Logic). Public policy: `web/privacy.html`.
**Release gate:** several features described here (sign-in, linking, usage sync, AI Logic, report-a-bad-result, web
deletion) land in later milestones. Before the first public release, verify each row against the shipped code and
remove anything not shipped. Re-check this file whenever a Firebase SDK or a Firestore field is added.

Common to both apps
- Data is encrypted in transit: **Yes** (TLS to Firebase).
- Users can request deletion: **Yes**, in the app (Profile, Delete account and all data) and at the web deletion page
  (`web/delete-account.html`).
- Data shared with third parties: **No**. Firebase/Google acts as a processor, which Play does not count as sharing.
- Ads / ad SDKs: **None**. No Analytics, no Crashlytics, no advertising ID.
- Data is not used for ads, marketing or profiling. Purposes: **App functionality**, plus **Account management** for
  sign-in data.
- Independent security review: No.

## Stay Focused (parent / main app)

Collection is optional: with no account nothing leaves the device, except an AI sentence if the user uses that feature.

| Play category | Data type | Collected | Shared | Optional | Purpose |
|---|---|---|---|---|---|
| Personal info | Email address | Yes | No | Yes (account) | Account management, App functionality |
| Personal info | Name (display name) | Yes | No | Yes (account) | Account management |
| Personal info | User IDs (Firebase uid) | Yes | No | Yes | Account management, App functionality |
| Device or other IDs | FCM token, Firebase installation ID | Yes | No | Yes | App functionality |
| App activity | Other user-generated content (the AI sentence; processed by Firebase AI Logic / Gemini; stored only if the user confirms "Report a bad result") | Yes | No | Yes | App functionality |
| App activity | Other actions (blocks, limits, schedules set while signed in and linked) | Yes | No | Yes | App functionality |

Not collected: location, contacts, messages, photos/videos, audio, files, calendar, health, financial info, web
browsing history, crash logs or diagnostics, installed apps of the parent phone.

## Stay Focused Kids (child app)

The child app only works linked to a parent. The data below goes to the linked parent's account.

| Play category | Data type | Collected | Shared | Optional | Purpose |
|---|---|---|---|---|---|
| App activity | App interactions (foreground app, daily per-app minutes and opens) | Yes | No | No | App functionality |
| App activity | Installed apps (launchable apps list: package and label) | Yes | No | No | App functionality |
| Device or other IDs | FCM token, Firebase installation ID, link code | Yes | No | No | App functionality |
| Device or other IDs | Device model and name | Yes | No | No | App functionality |
| App activity | Other actions (battery level, charging state, online/last-seen time; Play has no "device info" category) | Yes | No | No | App functionality |
| Personal info | User IDs (child uid) | Yes | No | No | App functionality |

The AI feature is not in the child app.

Notes
- The installed-apps list is declared; the app must not use `QUERY_ALL_PACKAGES` (launcher-intent query only).
- Usage Access and Accessibility are declared separately in the permissions declaration forms (other M10 stories).
- Families Policy: the child app is parent-installed, with no ads and no third-party SDKs beyond Firebase. Confirm the
  target-audience answers before submission.
