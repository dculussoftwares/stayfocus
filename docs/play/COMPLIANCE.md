# Play compliance checklist (Phase 1)

Mirror of section 8 of the design handoff (`design_handoff_stay_focused_phase1/Stay Focused Phase Plan.dc.html`).
Items marked **Automated** are enforced in CI by `./gradlew verifyManifests` (part of `ciCheck`), which audits the merged
release manifests of `:app` and `:kids` with the rules in
[`build-logic/convention/src/main/kotlin/ManifestAudit.kt`](../../build-logic/convention/src/main/kotlin/ManifestAudit.kt) (tests: [`build-logic/convention/src/test/kotlin/ManifestAuditTest.kt`](../../build-logic/convention/src/test/kotlin/ManifestAuditTest.kt),
wired in [`build-logic/convention/src/main/kotlin/ManifestAuditPlugin.kt`](../../build-logic/convention/src/main/kotlin/ManifestAuditPlugin.kt)). Everything else is a manual or Play Console step.

| # | Item | How it is met | Where | Check |
|---|---|---|---|---|
| 1 | Closed test: 12 testers for 14 days | Recruit during development; start the test before production access | Play Console | Manual (M10 release stories) |
| 2 | Accessibility API: never set `isAccessibilityTool` | Service config has no such attribute; `canRetrieveWindowContent=false` | [`core/blocking/src/main/res/xml/foreground_app_service.xml`](../../core/blocking/src/main/res/xml/foreground_app_service.xml) | CI checks `isAccessibilityTool` in every `<accessibility-service>` config; `canRetrieveWindowContent=false` is manual |
| 2b | Accessibility disclosure before Settings; Play declaration and demo video | In-app disclosure sheet, then Play Console declaration | `core/blocking`, Play Console | Manual (declaration + video) |
| 3 | Monitoring (stalkerware): `IsMonitoringTool`, persistent notification, parent to child only | `:kids` carries `isMonitoringTool=child_monitoring`; `:app` does not | [`kids/src/main/AndroidManifest.xml`](../../kids/src/main/AndroidManifest.xml) | CI checks the flag (present in `:kids`, absent in `:app`); persistent notification and parent-to-child only are manual |
| 4 | No `QUERY_ALL_PACKAGES`; app list via a launcher `<queries>` filter | Only `<queries>` for MAIN/LAUNCHER | [`core/usage/src/main/AndroidManifest.xml`](../../core/usage/src/main/AndroidManifest.xml) | CI checks the forbidden permission in both merged manifests; the launcher `<queries>` filter is manual |
| 4b | No battery-optimisation exemption request | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is never declared | all manifests | **Automated** |
| 5 | Foreground service `specialUse` with a written use-case | `:kids` `MonitoringService` and `:app` `BlockingService` (standard engine, blocking without Accessibility) both have `foregroundServiceType=specialUse` plus `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`; `:app` declares no other foreground service and requests only `FOREGROUND_SERVICE_SPECIAL_USE` | [`kids/src/main/AndroidManifest.xml`](../../kids/src/main/AndroidManifest.xml), [`app/src/main/AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) | **Automated** (`ManifestAudit`); the FGS declaration in Play Console for **each** app (with a short video of the block screen appearing for `:app`) is manual |
| 6 | Target API 36 | `TARGET_SDK = 36` in the convention plugin | [`build-logic/convention/src/main/kotlin/ProjectExtensions.kt`](../../build-logic/convention/src/main/kotlin/ProjectExtensions.kt) | **Automated** (`targetSdk == 36` for both apps) |
| 7 | Account deletion in-app and on the web | Profile screen action plus web page | [`web/src/pages/delete-account.astro`](../../web/src/pages/delete-account.astro), https://vesper.dculus.com/delete-account | Manual |
| 8 | Privacy policy and Data safety | Hosted policy; form answers | [`web/src/pages/privacy.astro`](../../web/src/pages/privacy.astro), https://vesper.dculus.com/privacy, [`docs/play/DATA_SAFETY.md`](DATA_SAFETY.md) | Manual |

Notes
- The bare `android.permission.FOREGROUND_SERVICE` permission is merged into `:app` by Firebase / Play services
  libraries. It declares no service, so the audit only rejects typed `FOREGROUND_SERVICE_*` permissions and any
  `foregroundServiceType` service in `:app`.
- Adding an item here that can be checked mechanically? Extend `ManifestAudit` and its test in the same PR.
