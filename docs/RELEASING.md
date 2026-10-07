# Releasing

A tag `vMAJOR.MINOR.PATCH` (or `vMAJOR.MINOR.PATCH-rcN`, N 1..98, the only pre-release form; it marks the GitHub Release as a pre-release) runs
`.github/workflows/release.yml`: signed AABs for `:app` and `:kids`, attached to a GitHub Release together with the R8
mapping files, and uploaded to the Play **internal** track (draft) when a Play service account is configured.

```bash
git tag v0.2.0 && git push origin v0.2.0
```

## Version scheme

`versionName` is the tag without the `v`. `versionCode = MAJOR*1_000_000 + MINOR*10_000 + PATCH*100 + build` (minor and patch 0..99). `build` is the
N of `-rcN` and 99 for a final tag, so `v0.2.0-rc1` < `v0.2.0` < `v0.2.1-rc1`.
`-PversionBuild` (0..99) overrides it, e.g. to re-upload the same version to Play. Local builds default to `0.1.0`. Logic and tests: `build-logic/convention/.../ReleaseVersion.kt`.

## One-time setup (a person)

Create these in the repo's `release` environment (Settings > Environments), never in the repo:

| Secret | What |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | `base64 -i upload.jks` of the **upload** key (Play App Signing holds the app signing key) |
| `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD` | keystore and key credentials |
| `GOOGLE_SERVICES_APP_PROD_BASE64`, `GOOGLE_SERVICES_KIDS_PROD_BASE64` | prod `google-services.json` per app (from the Terraform outputs, M10-04). Required: the workflow fails without them, so no release ships without the prod Firebase config |
| `PLAY_SERVICE_ACCOUNT_JSON` | optional: Play Console service account JSON with release permission; the Play upload is skipped without it |

Generate the upload key once (`keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 2048
-validity 10000`) and keep it and its passwords outside the repo (`*.jks` and `*.keystore` are git-ignored).

The workflow refuses tags that do not point at a commit on `main`. In the `release` environment also restrict deployment
tags to `v*` and require a reviewer, so a tag push alone cannot reach the secrets.

## Local release build

Without the `STAYFOCUSED_KEYSTORE_*` environment variables `./gradlew :app:bundleRelease :kids:bundleRelease` builds an
**unsigned** bundle (R8 still runs). To sign locally set `STAYFOCUSED_KEYSTORE_FILE`, `STAYFOCUSED_KEYSTORE_PASSWORD`,
`STAYFOCUSED_KEY_ALIAS` and `STAYFOCUSED_KEY_PASSWORD`. R8 rules: `config/proguard/proguard-rules.pro`.

## Install a bundle on a device

```bash
java -jar bundletool.jar build-apks --bundle=stayfocused-v0.2.0.aab --output=app.apks \
  --ks=upload.jks --ks-key-alias=upload   # signs the generated APKs; needed for a device install
java -jar bundletool.jar install-apks --apks=app.apks
```
