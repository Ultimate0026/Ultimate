# MacroBot

Android app that records taps and swipes, lets you edit every step, and replays them in a loop —
built for repetitive game tasks such as tower-defense farming. It can also look for things on screen
(image recognition) and tap them.

> Android only. iOS does not allow an app to tap or read the screen of another app.
> Automating a game may be against that game's terms of service — use at your own risk.

## Features

- **Record** taps and swipes over any app. Inputs are passed through to the game while recording so
  the game state follows along. Delays between inputs are captured automatically.
- **Edit each step**: position, press/swipe time, delay after, repeat count, priority, on/off, test-run it.
- **Priority order**: every step has a priority number (lower runs first). Reorder with the arrows.
- **Two run modes**
  - *Sequence*: run all steps in priority order, loop N times or forever.
  - *Reactive*: every cycle, run only the highest-priority step whose condition is met
    (e.g. "if the *Start wave* button is visible, tap it", else fall back to a plain tap).
- **Image recognition** (OpenCV template matching): `Tap image` and `Wait for image` steps.
  Drag a box on the live screen to capture the template; tune the match threshold per step.
- **Floating controls** (RUN / REC / CROP) that sit on top of your game.

## Build

CI builds a debug APK on every push: GitHub → Actions → *Build APK* → download the
`MacroBot-debug-apk` artifact and install it on your phone.

Locally: open the folder in Android Studio, or run `./gradlew assembleDebug`.

## First-time setup on the phone

1. Install the APK.
2. **Accessibility service**: Settings → Accessibility → MacroBot → On.
   (On Android 13+ for sideloaded apps first do Settings → Apps → MacroBot → ⋮ → *Allow restricted settings*.)
3. **Screen capture** (only needed for image steps): tap *Grant screen capture* in the app.
4. Create a macro, tap *Floating controls*, open your game, press **REC**, play, press **DONE**.
5. Press **RUN** (or *Start* in the app). **STOP** ends it.

## Image steps

1. Add a step of type *Tap image* or *Wait for image*, tap *Pick image from screen*.
2. Open your game, press **CROP** on the floating bar, drag a box around the target (a button, an icon).
3. Reopen the app; the thumbnail shows on the step. Matching is done at the screen resolution the
   template was captured at, so capture on the same phone/orientation you will run on.

## Layout

```
app/src/main/java/com/ultimate/macrobot/
  model/    Macro, Step, enums (JSON-serialised)
  data/     MacroRepository (macros.json + template PNGs)
  engine/   MacroRunner (sequence/reactive loop), ImageMatcher (OpenCV)
  service/  MacroAccessibilityService (gestures + overlays), OverlayController, ScreenCaptureService
  ui/       Compose screens: Home, Editor, StepDialog
```
