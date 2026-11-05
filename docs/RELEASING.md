# Releasing

A tag `vMAJOR.MINOR.PATCH` (or `vMAJOR.MINOR.PATCH-rcN`, N 1..98, the only pre-release form; it marks the GitHub Release as a pre-release) runs
`.github/workflows/release.yml`: signed AABs for `:app` and `:kids`, attached to a GitHub Release together with the R8
mapping files, and the Vesper bundle uploaded to the Play **internal** track (draft). Vesper Kids is not on Play yet, so only its
GitHub Release bundle is made.

```bash
git tag v0.2.0 && git push origin v0.2.0
```

## Version scheme

`versionName` is the tag without the `v`. `versionCode = MAJOR*1_000_000 + MINOR*10_000 + PATCH*100 + build` (minor and patch 0..99). `build` is the
N of `-rcN` and 99 for a final tag, so `v0.2.0-rc1` < `v0.2.0` < `v0.2.1-rc1`.
`-PversionBuild` (0..99) overrides it, e.g. to re-upload the same version to Play. Local builds default to `0.1.0`. Logic and tests: `build-logic/convention/.../ReleaseVersion.kt`.

## One-time setup (a person)

1. **Upload key.** Play needs it valid past 22 October 2033; this one is valid for about 100 years:

   ```bash
   keytool -genkeypair -v -keystore upload.jks -storetype PKCS12 -alias upload \
     -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 36500 \
     -dname "CN=<name>, O=<organization>, C=<country code>"
   keytool -list -v -keystore upload.jks -alias upload   # check "Valid until"
   ```

   Keep `upload.jks` and its password outside the repo, with a backup (`*.jks` and `*.keystore` are git-ignored). With
   PKCS12 the key password is the keystore password.
2. **Secrets** in the repo's `release` environment (Settings > Environments > release), never in the repo:

   | Secret | What |
   |---|---|
   | `UPLOAD_KEYSTORE_BASE64` | `base64 -i upload.jks` of the **upload** key (Play App Signing holds the app signing key) |
   | `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD` | keystore password, `upload`, same password again |

3. **Play upload access.** No JSON key: the `play-internal-track` job logs in keylessly as
   `play-publisher@vesper-prod-31275.iam.gserviceaccount.com` (`GCP_PLAY_SA` in `infra/prod.env`, created by
   `infra/seed`). In Play Console > Users and permissions, invite that address with access to the Vesper app and the
   permissions *Release to testing tracks* and *Manage testing tracks*.
4. **First upload by hand.** The Play API cannot create an app's first release, so upload the first AAB (from the GitHub
   Release) in Play Console once. Later tags upload by themselves.

Terraform creates the `release` environment (`infra/github/repository.tf`): only `v*` tags may deploy to it, and the
workflow also refuses tags that do not point at a commit on `main`. There is no approval step.

## Local release build

Without the `VESPER_KEYSTORE_*` environment variables `./gradlew :app:bundleRelease :kids:bundleRelease` builds an
**unsigned** bundle (R8 still runs). To sign locally set `VESPER_KEYSTORE_FILE`, `VESPER_KEYSTORE_PASSWORD`,
`VESPER_KEY_ALIAS` and `VESPER_KEY_PASSWORD`. R8 rules: `config/proguard/proguard-rules.pro`.

## Install a bundle on a device

```bash
java -jar bundletool.jar build-apks --bundle=vesper-v0.2.0.aab --output=app.apks \
  --ks=upload.jks --ks-key-alias=upload   # signs the generated APKs; needed for a device install
java -jar bundletool.jar install-apks --apks=app.apks
```
