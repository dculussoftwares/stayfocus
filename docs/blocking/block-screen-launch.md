# Block screen launch paths (M2-03)

`BlockScreenLauncher.show(pkg, decision)` (in `:core:blocking`) runs on the main thread:

1. `performGlobalAction(GLOBAL_ACTION_HOME)`: the blocked app leaves the front.
2. `startActivity(BlockActivity, FLAG_ACTIVITY_NEW_TASK)` from the accessibility service context.
3. After 350 ms, if `BlockScreenState.visible` is still false (or the start threw), the launcher adds a full-screen
   `TYPE_ACCESSIBILITY_OVERLAY` window with the same Compose screen (`AccessibilityOverlay`).

Android drops disallowed background activity starts silently (no exception), so step 3 checks the outcome instead of
assuming it. No extra permission is needed for either path: `TYPE_ACCESSIBILITY_OVERLAY` is available to any enabled
accessibility service, and `BlockActivity` is not exported.

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
