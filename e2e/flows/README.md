# End-to-end flows (Maestro)

User journeys driven on a real Android system with [Maestro](https://maestro.dev). Two levels of on-device testing exist:

| Level | Where | Runs |
|---|---|---|
| Instrumented tests (Compose UI / AndroidX Test) | `app/src/androidTest`, `kids/src/androidTest`, harness in `:core:testing` | `./gradlew ciDevicesGroupDebugAndroidTest` (Gradle Managed Devices `pixel6Api26` + `pixel6Api36`, headless); CI job `instrumented-tests` |
| End-to-end flows (Maestro) | `e2e/flows/` | `maestro test e2e/flows`; CI job `e2e-smoke` (PRs) and `e2e.yml` (full suite) |

## Run locally

1. Install Maestro (`curl -fsSL "https://get.maestro.mobile.dev" | bash`) and start an emulator (API 36 recommended).
2. `./gradlew :app:installDebug :kids:installDebug`
3. `maestro test e2e/flows` (every flow listed in `e2e/flows/config.yaml`), or one file: `maestro test e2e/flows/smoke/navigation_tabs.yaml`.

CI uses `e2e/scripts/run-flows.sh` (bash 4+, so Linux), which also records a video per flow and writes a JUnit report to `e2e/results/`.

## Layout

- `flows/smoke/`: the PR gate (`e2e-smoke` job, must stay under ~5 minutes in total). Launch both apps and the navigation tabs.
- `flows/config.yaml`: which folders `maestro test e2e/flows` runs. Add a glob when you add a folder of flows.
- `flows/_shared/`: helper flows that other flows call with `runFlow`. Not listed in `config.yaml`, so they never run alone.

## Writing flows

- Select by visible text, content description or test tag, never by coordinates.
- Start with `launchApp` + `clearState: true` so flows don't depend on each other.
- Where a Settings screen would otherwise be needed, grant the permission with `adb shell appops set` / `pm grant` in a setup step instead of driving the system UI.
- **Convention:** a story that adds or changes a user journey adds or extends a flow here. Journeys to cover as they land:
  onboarding + permissions (M5), create each block type (M4), Take a break + block screen (M2), account sign-in (M6, against the
  Auth emulator), linking (M7, two emulators where feasible, otherwise a documented manual check in `docs/qa/pending-manual-checks.md`).
- Put quick, cheap journeys in `smoke/`; slower ones in their own folder (and in `config.yaml`) so they only run in `e2e.yml`.
