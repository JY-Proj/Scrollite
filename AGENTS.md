# Scrollite — notes for contributors and AI agents

Scrollite is an Android app that tracks and limits time spent on short-form
video (Instagram Reels, YouTube Shorts) using an accessibility service. Keep
this file short and current: update it in the same PR when a rule changes.

## Commands

```sh
./gradlew :app:testDebugUnitTest        # unit tests (JVM, fast)
./gradlew :app:assembleDebug            # build
./gradlew :app:installDebug             # install on a running emulator/device
./gradlew :app:connectedDebugAndroidTest  # instrumented tests (Room DAO/migrations)
```

Force-stopping (and sometimes reinstalling) the app turns the accessibility
service off on the emulator. Re-enable it with:

```sh
adb shell settings put secure enabled_accessibility_services \
  com.jynx.scrollite/com.jynx.scrollite.ReelsAccessibilityService
```

## How it fits together

- `ReelsAccessibilityService` — receives accessibility events, asks the
  foreground app's detector which section is on screen, times usage sessions,
  enforces the daily limit (overlay, then eject), and captures watched videos.
- `tracking/` — `AppDetector` + one detector per app in `tracking/apps/`,
  `SessionTracker` (pure timing logic), `Throttle` and `IdleRecheck`
  (detection scheduling), `NodeQueries` (accessibility-tree helpers).
- `data/` — Room (`AppDatabase`, schemas exported to `app/schemas/`),
  DataStore stores (`ScreenTimeStore` for limits, `AppearanceStore` for theme),
  `Platform` / `Section` enums.
- `ui/limit/` — the limit overlay (XML views, drawn by the service).
- `ui/` (Compose) — Today / Stats / Settings tabs; `ui/theme/` holds the
  design tokens; `ui/components/` shared pieces.

## Rules

**Accessibility events.** `res/xml/accessibility_service_config.xml` only
subscribes to window-state, window-content and view-scrolled events (least
privilege, and fewer wake-ups). A feature that needs another event type must
add it there, or its events silently never arrive.

**Battery.** Detection reads another app's view tree, which is expensive.
Route it through `detectNow()`: it's throttled to `DETECTION_INTERVAL_MS`
with a trailing run (the last event is never dropped), plus an
`IdleRecheck` every `IDLE_RECHECK_MS` while a session is open and the app is
quiet. Anything that stops tracking must go through `stopTracking()`, which
cancels both. Keep per-event work minimal.

**Adding an app** (e.g. TikTok): write a detector in `tracking/apps/`,
register it in `AppDetectors`, and add its `Platform` entry with its own
section names. Tracking, limits, overlay and dashboard need no changes.

**Colors and design tokens — never hardcode.** The brand palette (hex values)
lives only in `res/values/colors.xml`. The XML overlay uses role colors from
`values/colors.xml` + `values-night/colors.xml`; Compose reads the palette in
`ui/theme/Color.kt` and maps it to roles there. Screens use roles only.
Alphas, radii and blur for the overlay are in `dimens_limit_overlay.xml`.
Current usage: soft Teal `#D3FFF9` for selected states, chart teal `#51CDBC`
for bars, deep turquoise for progress/links, Gray Cyan only in the logo.

**Limit and extra-time rules.** All changes apply immediately. Stricter
changes never ask. Looser changes (raising the limit, more extra time) ask
first with "keep" as the main action, and the wording gets gently sadder with
each loosening that day (`LooseningTone`). Never block — friction, not
punishment.

**Testability.** Put decision logic in small classes without Android
dependencies (`SessionTracker`, `Throttle`, `IdleRecheck`, `LimitOverlaySpec`,
`DailyLimitOptions`, `ScreenTimeUiState.of`) and unit-test them.

## Product direction

- Goal: analytics on what kind of content users are hooked on.
- Planned content pipeline: up to 3 screenshots per video → on-device text
  recognition + image labels (ML Kit) → topic classifier (on-device Gemini
  Nano where available, else text-only to a cloud LLM) → store topic +
  signals + classifier version → delete the images. Keep each stage behind an
  interface so models can be swapped and history re-classified.
- Privacy: screenshots never leave the device; disclose what's analyzed in
  the in-app accessibility explanation (Play policy).

## Git

Branch from `main` as `<name>/<topic>`. Don't push or open PRs without the
author's go-ahead.
