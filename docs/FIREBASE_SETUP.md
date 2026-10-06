# Firebase setup (forks and local builds)

Stay Focused builds and runs **without** any Firebase configuration. In that case the account, device linking and cloud AI
features show "Not available in this build", and blocking works normally. Add a configuration only if you want those
features in your own build.

## How the build decides

The `stayfocused.android.application` convention plugin applies the `com.google.gms.google-services` plugin only when the
app module contains a `google-services.json`:

- `app/google-services.json` for the Stay Focused app (`com.dculus.stayfocused`)
- `kids/google-services.json` for the Kids app (`com.dculus.stayfocused.kids`)

Both files are git-ignored. Never commit them. At runtime, `FirebaseAvailability.isConfigured` in `:core:sync` is true
only when a Firebase app was initialised from that file.

## Option A: your own project through Terraform

1. Create a Firebase project and a Terraform workspace for it.
2. Point `infra/firebase` (added by the M6 and M7 infra stories) at your project and apply it through GitHub Actions as
   described in `docs/INFRASTRUCTURE.md`.
3. Download the Android app configs from the Firebase console into the two paths above.

## Option B: manual, for local use only

1. In the Firebase console, create a project and add two Android apps with the package names above.
2. Download each `google-services.json` into `app/` and `kids/`.
3. Build as usual (`./gradlew assembleDebug`).

## App Check

Both apps install App Check at startup (`AppCheckInstaller`), so Firestore and the callable Functions can enforce it:

- **Release builds** use the Play Integrity provider. Link the project to your Play app in the Firebase console.
- **Debug builds** use the debug provider. The SDK generates a token per install and logs it (Logcat tag
  `DebugAppCheckProvider`); register it in the Firebase console. No token is built into an APK: a token-bearing APK could be
  extracted and used from an unverified device. Treat registered debug tokens as secrets, never share debug builds that
  ran with one, and delete tokens when you finish debugging.
