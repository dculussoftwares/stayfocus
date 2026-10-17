# Play compliance checklist (Phase 1)

Mirror of section 8 of the design handoff (`design_handoff_stay_focused_phase1/Stay Focused Phase Plan.dc.html`).
Items marked **Automated** are enforced in CI by `./gradlew verifyManifests` (part of `ciCheck`), which audits the merged
release manifests of `:app` and `:kids` with the rules in
`build-logic/convention/src/main/kotlin/ManifestAudit.kt` (tests: `build-logic/convention/src/test/kotlin/ManifestAuditTest.kt`,
wired in `build-logic/convention/src/main/kotlin/ManifestAuditPlugin.kt`). Everything else is a manual or Play Console step.

| # | Item | How it is met | Where | Check |
|---|---|---|---|---|
| 1 | Closed test: 12 testers for 14 days | Recruit during development; start the test before production access | Play Console | Manual (M10 release stories) |
| 2 | Accessibility API: never set `isAccessibilityTool` | Service config has no such attribute; `canRetrieveWindowContent=false` | `core/blocking/src/main/res/xml/foreground_app_service.xml` | **Automated** (every `<accessibility-service>` config) |
| 2b | Accessibility disclosure before Settings; Play declaration and demo video | In-app disclosure sheet, then Play Console declaration | `core/blocking`, Play Console | Manual (declaration + video) |
| 3 | Monitoring (stalkerware): `IsMonitoringTool`, persistent notification, parent to child only | `:kids` carries `isMonitoringTool=child_monitoring`; `:app` does not | `kids/src/main/AndroidManifest.xml` | **Automated** (flag present in `:kids`, absent in `:app`) |
| 4 | No `QUERY_ALL_PACKAGES`; app list via a launcher `<queries>` filter | Only `<queries>` for MAIN/LAUNCHER | `core/usage/src/main/AndroidManifest.xml` | **Automated** (forbidden in both merged manifests) |
| 4b | No battery-optimisation exemption request | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is never declared | all manifests | **Automated** |
| 5 | Foreground service `specialUse` with a written use-case; none on the parent phone | `:kids` `MonitoringService` has `foregroundServiceType=specialUse` plus `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`; `:app` declares no typed FGS service or typed FGS permission | `kids/src/main/AndroidManifest.xml` | **Automated**; FGS declaration in Play Console is manual |
| 6 | Target API 36 | `TARGET_SDK = 36` in the convention plugin | `build-logic/convention/src/main/kotlin/ProjectExtensions.kt` | **Automated** (`targetSdk == 36` for both apps) |
| 7 | Account deletion in-app and on the web | Profile screen action plus web page | `web/delete-account.html` | Manual |
| 8 | Privacy policy and Data safety | Hosted policy; form answers | `web/privacy.html`, `docs/play/DATA_SAFETY.md` | Manual |

Notes
- The bare `android.permission.FOREGROUND_SERVICE` permission is merged into `:app` by Firebase / Play services
  libraries. It declares no service, so the audit only rejects typed `FOREGROUND_SERVICE_*` permissions and any
  `foregroundServiceType` service in `:app`.
- Adding an item here that can be checked mechanically? Extend `ManifestAudit` and its test in the same PR.
