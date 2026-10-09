# Blocking engine test matrix (M2-09)

How the engine behaves when the platform interrupts it. "Design" says why it is expected to work and which automated test
covers it; the device columns are filled in by whoever runs the manual check
(see `docs/qa/pending-manual-checks.md`). Result key: `OK`, `FAIL (issue #n)`, `n/a`, `-` (not run yet).

Devices: **Pixel** = Pixel emulator or phone, API 36; **API 26** = emulator with API 26 (minSdk); **Samsung** = One UI
phone; **Xiaomi** = MIUI/HyperOS phone.

## What the engine keeps where

| State | Stored in | Survives |
|---|---|---|
| Blocks, locked apps, breaks, focus session | Room / DataStore | reboot, process death, force-stop |
| Cycle ("use, then rest") progress | Room (`CycleStateStore`) | reboot, process death |
| Foreground app, current stay, shown block screen | memory (`ForegroundAppTracker`, `BlockingEngine`) | nothing; rebuilt from the next window event |

The engine runs inside the accessibility service (`ForegroundAppService`): it starts in `onServiceConnected` and stops in
`onUnbind`/`onDestroy`. `EngineHealthMonitor.health` (`serviceBound`, `lastEventAt`) is what M5-03 and M6-05 read.

## Matrix

| # | Scenario | Expected | Design / automated coverage | Pixel | API 26 | Samsung | Xiaomi |
|---|---|---|---|---|---|---|---|
| 1 | Reboot with a block active | Service is rebound by the system, engine reads blocks from storage, the next window change into a blocked app shows the block screen. `serviceBound` is false until then. | Storage is the source of truth; `onServiceConnected` resets the tracker and starts the engine. `EngineHealthMonitorTest`, `BlockingEngineTest` | - | - | - | - |
| 2 | Process death (`adb shell am kill`) during a block, a break and a cycle | Countdowns and cycle progress are right after reopening; service rebinds by itself | Break end and cycle state are persisted timestamps; `BlockingEngineTest` (restart of the run) | - | - | - | - |
| 3 | Force-stop from Settings | Blocking stops (Android turns the service off). The app shows "Accessibility is off" via `AccessibilityStatus`/`serviceBound = false`, with the way to turn it on again | `AccessibilityStatusTest`, `EngineHealthMonitorTest` | - | - | - | - |
| 4 | Service toggled off and on | Engine stops, then restarts without double counting time; stale foreground app is dropped | `BlockingEngineTest` (stop/start), tracker reset in `onServiceConnected` | - | - | - | - |
| 5 | Split-screen with a blocked app in one pane | Block screen appears when the blocked app's window becomes active; the other pane is unaffected | Window events come from both panes; `ForegroundAppTrackerTest` | - | - | - | - |
| 6 | Picture-in-picture of a blocked app (video call or player) | The block screen takes the foreground; the PiP window is closed or covered. Known risk: a PiP window that keeps playing is not an activity change; if it still plays, file an issue | Manual only | - | - | - | - |
| 7 | Update of a blocked app (Play Store or `adb install -r`) | Block persists (stored by package name); the service stays bound | Package name does not change; nothing to re-register | - | - | - | - |
| 8 | Update of Stay Focused itself | The system rebinds the service after the update; blocks and cycles intact | As row 1 | - | - | - | - |
| 9 | Device language change while a block is shown | Block screen and app text switch language, block stays | Resources are resolved per configuration; the engine holds no localised text | - | - | - | - |
| 10 | Clock or time-zone change | Schedules re-evaluate at once; the jump is never counted as usage | `BlockingEngineTest` (clock changes), M2-08 | - | - | - | - |
| 11 | Keyboard, notification shade, other overlays | Never counted or blocked as the foreground app | `ForegroundAppTrackerTest` | - | - | - | - |
| 12 | OEM background kill (battery saver, MIUI "autostart" off) | Service stays bound after the app is swiped from recents; if it does not, M5-06 guidance applies | M5-06 | n/a | n/a | - | - |

## Known limits found while writing this

- After a rebind the engine only learns the foreground app from the next window-state event (the service does not read
  window content, by Play policy), so a blocked app that was already open when the service came back is blocked at the
  next app switch or screen on, not instantly.
- Rows 5 to 9 and 12 need a real or emulated device; they are listed in `docs/qa/pending-manual-checks.md`.
