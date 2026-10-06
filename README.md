# Stay Focused

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Stay Focused is a free, open-source Android app blocker. It helps you block distracting apps on your own phone, and a
companion **Stay Focused Kids** app lets a parent set limits on a child's phone.

Phase 1 is under construction. The plan, module map and conventions are in
[`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md); the rules for contributors and coding agents are in
[`AGENTS.md`](AGENTS.md).

## Build

Requirements: Android Studio (or the Android SDK with platform 36) and any JDK that can run Gradle. Gradle downloads
the JDK 17 toolchain on its own.

```bash
./gradlew assembleDebug             # builds :app and :kids
./gradlew checkFeatureIsolation     # no :feature:* module may depend on another :feature:*
```

The project builds without `google-services.json`. Forks that want Firebase bring their own file; never commit it.

## Commit and push hooks

Install once per clone (and once per worktree): `scripts/dev/setup-hooks.sh`. It installs [pre-commit](https://pre-commit.com)
and registers the commit and push hooks. Commit: whitespace/YAML/JSON hygiene, gitleaks, ktlint, `terraform fmt`/`validate`/tflint
for `infra/`, actionlint, and a guard against `google-services.json`, keystores, `*.tfstate` and `.env*`. Push:
`./gradlew spotlessCheck detekt testDebugUnitTest`. Run the commit checks on everything with `pre-commit run --all-files` (and the push checks with
`pre-commit run --hook-stage pre-push --all-files`); CI runs the commit checks. ktlint needs a JDK and the Terraform hook needs `terraform` on your PATH.

## Modules

`:app` (Stay Focused) and `:kids` (Stay Focused Kids) sit on top of `:core:*` (model, ui, data, usage, blocking, sync,
testing) and `:feature:*` (onboarding, home, block, devices, insights, account). Build logic lives in `build-logic/`
as convention plugins (`stayfocused.android.*`, `stayfocused.jvm.library`), and every version is in
`gradle/libs.versions.toml`.

## License

GPL-3.0. See [`LICENSE`](LICENSE).
