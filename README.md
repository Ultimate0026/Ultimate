# MacroBot

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

An Android app that **records your taps and swipes, lets you edit every step, and replays them in a
loop**. It can also look for things on screen (image recognition) and tap them. Built for repetitive
game tasks such as tower-defense farming, but it works over any app.

- **Record** inputs over any app; delays between them are captured automatically.
- **Edit each step**: position, press/swipe time, delay, repeat count, priority, on/off, test-run.
- **Priority order**: lower number runs first; reorder with the arrows.
- **Two run modes**: *Sequence* (everything in priority order, looped) and *Reactive* (each cycle, run only
  the highest-priority step whose condition is met).
- **Image recognition**: `Tap image` and `Wait for image` steps using OpenCV template matching.
- **Floating RUN / REC / CROP bar** that stays on top of your game.
- **Private**: no accounts, no analytics. Macros and screenshots stay on your phone. The only network use is an optional "is there a newer release?" check on GitHub.

> **Android only.** iOS does not let an app tap or read other apps.
>
> **Use responsibly.** Many games, including Roblox experiences, forbid automation in their terms of
> service and may suspend accounts that use it. You are responsible for how you use this app.

## Install

1. Open the [latest release](../../releases/latest) on your phone and download `MacroBot-*.apk`.
2. Tap the file and allow "Install unknown apps" for your browser/Files app when Android asks.
3. Open MacroBot and follow the **Setup** card:
   1. **Accessibility service** - needed to perform taps. Settings > Accessibility > MacroBot > On.
      On Android 13+ you may first need Settings > Apps > MacroBot > ⋮ > **Allow restricted settings**.
   2. **Screen capture** - only needed for image steps. Tap *Grant screen capture*.
   3. **Floating controls** - shows the RUN / REC / CROP bar.

Requires Android 8.0 (API 26) or newer.

## Quick start

1. **New macro**, open it.
2. Tap **Floating controls**, switch to your game, press **REC**, play the inputs you want, press **DONE**.
3. Back in MacroBot, tweak the steps (delays, priority, repeat...). Use the ▶ button on a step to test it.
4. Press **Start** (or **RUN** on the floating bar). **STOP** ends it. Closing the bar (X) also stops it.

### Run modes

| Mode | Behaviour |
| --- | --- |
| **Sequence** | Runs every enabled step once per loop, lowest priority number first. Loops N times or forever, with a delay between loops. |
| **Reactive** | Each cycle, runs only the first step (lowest priority number) whose condition is met, then starts over. Tap/Swipe steps are always met, so give them the highest number to act as a fallback. A visible *Wait for image* step holds back every step below it. |

### Always-on image watching

Set a macro to **Reactive** mode with *Tap image* steps and it keeps watching the screen until you press STOP,
tapping whenever a target appears. *Check screen for images every (ms)* controls how often it looks:
`10000` checks every 10 seconds, which is much easier on the battery and CPU than the default 1 second.
The phone's screen has to stay on and MacroBot's accessibility service has to stay enabled.

### Image steps

1. In a macro, tap **Image step** (or **Tap / swipe** and pick *Tap image* / *Wait for image* as the type), then press *Pick image from screen*.
2. Switch to your game, press **CROP**, and drag a box around the target (a button or icon).
3. Reopen MacroBot - a thumbnail appears on the step. Raise the match threshold if it taps the wrong
   thing, lower it if it misses.

Tips: crop tightly around something distinctive; capture on the same phone and orientation you will run
on (matching is done at screen resolution); avoid regions with animated or changing numbers.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Accessibility option is greyed out | Settings > Apps > MacroBot > ⋮ > *Allow restricted settings*, then retry. |
| Taps land in the wrong place | Re-record after changing screen rotation or display size; coordinates are absolute. |
| Image is never found | Re-crop it, lower the threshold (try 0.75), and make sure screen capture is ON. |
| Image taps the wrong thing | Crop a more distinctive area and raise the threshold (0.9+). |
| Macro stops by itself | Android may have turned the accessibility service off (battery savers do this). Re-enable it and exclude MacroBot from battery optimisation. |
| Games detect and block it | Some games block gestures from accessibility services. Nothing here works around that. |

## Privacy

Macros and cropped images are stored in the app's private storage and never uploaded. Screen capture is
only used for matching images, and a captured frame is never saved except the region you crop.

The app has the `INTERNET` permission for exactly one thing: on launch it asks GitHub's public API for the
latest release of this repo and compares version numbers. Nothing about you, your phone or your macros is
sent. Turn it off with the **Check for updates on launch** switch on the home screen.

### Updating

When a newer release exists, a popup offers **Download**, which opens the release page; tap the `.apk` to
install over the current version. This works only if every release is signed with the same key (see
*Releasing* below).

## Building from source

```
./gradlew testDebugUnitTest assembleDebug
```
Open the folder in Android Studio, or let GitHub Actions build it: every push uploads a
`MacroBot-debug-apk` artifact (sign-in required to download).

```
app/src/main/java/com/ultimate/macrobot/
  model/    Macro, Step, enums (JSON-serialised)
  data/     MacroRepository (macros.json + template PNGs)
  engine/   MacroRunner (sequence/reactive loop), ImageMatcher (OpenCV)
  service/  MacroAccessibilityService (gestures + overlays), OverlayController, ScreenCaptureService
  ui/       Compose screens: Home, Editor, StepDialog
```

## Releasing (maintainers)

1. On GitHub: **Releases > Draft a new release**, create a tag such as `v1.0.0`, **Publish**.
2. The *Release APK* workflow builds the app and attaches `MacroBot-v1.0.0.apk` to that release.

**Signing:** Android only installs an update over an existing install if both APKs have the same signing
key. Create a key once and store it as repository secrets (Settings > Secrets and variables > Actions):

```
keytool -genkeypair -v -keystore release.keystore -alias macrobot -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 release.keystore      # paste as KEYSTORE_BASE64
```
Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Keep the keystore and passwords
backed up and **never commit them**. Without the secrets the workflow still builds, but signs with the
debug key, so users would have to uninstall before moving to a properly signed build.

## License

[MIT](LICENSE)
