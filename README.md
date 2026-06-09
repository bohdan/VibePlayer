# Vibe Player — Android

A task-load-responsive procedural synthwave player. Native Android shell
(Kotlin) around the proven Web Audio engine, with a foreground media service
so playback continues with the screen off or the app in the background.

## Build & run

1. Open this folder in Android Studio (Hedgehog or newer).
2. Let Gradle sync (Studio will set up the wrapper automatically).
3. Plug in a device or start an emulator, press Run.

Min SDK 26 (Android 8.0), target SDK 34.

## How it works

- `assets/index.html` — the entire player: UI + procedural synth engine
  (7 energy tiers, generative 4-bar sections, shared-filter audio graph,
  1.5 s scheduling lookahead so timer throttling can't cause dropouts).
- `MainActivity.kt` — fullscreen WebView. Deliberately does NOT forward
  `onPause()` to the WebView so audio keeps running in the background.
- `PlaybackService.kt` — foreground service (mediaPlayback type) + partial
  wake lock, started/stopped from JS via the `AndroidBridge` interface.
  This is what keeps Android from freezing the process during playback.

## Wiring up a real task manager later

Add a method to `MainActivity.Bridge` that fetches tasks (Todoist/Linear
API), then call into the page with
`webView.evaluateJavascript("setTaskLoad(5, 3)", null)` — and expose a
small `setTaskLoad(tasks, urgency)` function in index.html that sets the
two variables and calls `update()`.
