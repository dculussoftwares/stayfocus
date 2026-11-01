# Block screen launch paths (M2-03)

`BlockScreenLauncher.show(pkg, decision)` (in `:core:blocking`) runs on the main thread, through a `BlockSurface`
chosen by the engine mode (see below):

1. `BlockSurface.goHome()`: the blocked app leaves the front.
2. `startActivity(BlockActivity, FLAG_ACTIVITY_NEW_TASK)`.
3. After 350 ms, if `BlockScreenState.visible` is still false (or the start threw), the launcher adds a full-screen
   overlay window of the surface's type with the same Compose screen (`BlockOverlayWindow`).

## Surfaces

| Mode | Surface | Home | Window type | Needs |
|---|---|---|---|---|
| Accessibility on | `AccessibilitySurface` | `performGlobalAction(GLOBAL_ACTION_HOME)` | `TYPE_ACCESSIBILITY_OVERLAY` | the accessibility service |
| Standard (no Accessibility) | `OverlaySurface` | `Intent(ACTION_MAIN).addCategory(CATEGORY_HOME)`, `NEW_TASK` | `TYPE_APPLICATION_OVERLAY` | Display over other apps |

Accessibility wins whenever its service is attached. Without it, the launcher uses `OverlaySurface` if
`Settings.canDrawOverlays` is true, otherwise it logs "no block surface available" and shows nothing. Granted
"Display over other apps" also exempts the app from the background activity start limits, so step 2 is allowed from
the foreground service of the standard engine. `TYPE_APPLICATION_OVERLAY` windows sit below System UI (status bar,
notification shade), which the tracker already ignores.

Android drops disallowed background activity starts silently (no exception), so step 3 checks the outcome instead of
assuming it. `TYPE_ACCESSIBILITY_OVERLAY` needs no extra permission (any enabled accessibility service may use it),
and `BlockActivity` is not exported.

## Path per API level

| API | Rules in force | Expected path | Verified |
|---|---|---|---|
| 26-28 | No background-start restriction | Activity | pending |
| 29-33 | Background starts restricted (Q); the service has no visible window of its own | Activity if the system allows it, otherwise overlay | pending |
| 34 | Stricter background-activity-launch rules | Activity if allowed, otherwise overlay | pending |
| 35-36 | Launch checks also consider the caller's task and top-app state | Activity if allowed, otherwise overlay | pending |

The "Verified" column is filled from the manual run listed in `docs/qa/pending-manual-checks.md` (M2-03): it needs
emulators on API 29, 34, 35 and 36 and a screen recording. Whatever the platform decides, the overlay fallback
guarantees the block screen is shown.

## Not covered here

- The overlay is only removed by "Go to home screen", back, or when the decision turns to Allow. Removing it when the user
  leaves the blocked app by other means is done by the engine story through `BlockScreenLauncher.dismissOverlay()`.
- `BlockDecisionSource` defaults to a time-based check (the block ends at `until`). The engine story binds the real
  `RuleEvaluator`.
