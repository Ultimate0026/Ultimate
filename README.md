# Ultrebo

[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

An Android app that **records your taps and swipes, lets you edit every step, and replays them in a
loop**. It can also look for things on screen (image recognition) and tap them. Built for repetitive
game tasks such as tower-defense farming, but it works over any app.

- **Record** inputs over any app; delays between them are captured automatically.
- **Edit each step**: position, press/swipe time, delay, repeat count, priority, on/off, test-run.
- **Priority order**: lower number runs first; reorder with the arrows.
- **Two run modes**: *Sequence* (everything in priority order, looped) and *Reactive* (each cycle, run only
  the highest-priority step whose condition is met).
- **Image recognition**: `Tap image` and `Wait for image` steps using OpenCV template matching.
- **Text recognition**: `Tap text` and `Wait for text` steps - just type the words to look for (on-device OCR).
- **Always-watching** image/text steps that react to pop-ups while your macro runs.
- **Floating RUN / REC / CROP bar** that stays on top of your game.
- **Private**: no accounts, no analytics. Macros and screenshots stay on your phone. The only network use is an optional "is there a newer release?" check on GitHub.

> **Android only.** iOS does not let an app tap or read other apps.
>
> **Use responsibly.** Many games, including Roblox experiences, forbid automation in their terms of
> service and may suspend accounts that use it. You are responsible for how you use this app.

## Install

1. Open the [latest release](../../releases/latest) on your phone and download `Ultrebo-*.apk`.
2. Tap the file and allow "Install unknown apps" for your browser/Files app when Android asks.
3. Open Ultrebo. A **Get started** guide on the home screen walks you through the permissions it needs:
   1. **Accessibility service** - needed to perform taps. The guide opens the right settings page. On
      Android 13+ you may first need **Allow restricted settings** (the guide has a button for that too).
   2. **Notifications** (Android 13+) - for the small "screen capture is on" notification.
   3. **Screen capture** (optional) - only for image and text steps; grant it from the guide or the home screen.

   The guide checks each permission itself and disappears once the required ones are on.

Requires Android 8.0 (API 26) or newer.

## Why each permission?

Ultrebo asks for a few sensitive permissions because tapping for you needs them. Each one is used for exactly this and nothing else:

| Permission | What it is used for |
| --- | --- |
| **Accessibility service** | How every auto-clicker taps for you. Used only to perform your macros' taps and swipes and to show the floating RUN / REC bar. It does not request permission to read other apps' content. |
| **Screen capture** (optional) | Only for image and text steps. Frames are checked in memory on your phone and discarded; the only image saved is the region you crop. Android asks again each time the app restarts. |
| **Notifications** | Android requires a visible notice while screen capture is on. |
| **Internet** | Asking GitHub if a newer release exists (can be turned off) and downloading it if you tap Update. Google ML Kit, used for text recognition, runs on the phone but may send anonymous usage statistics. |
| **Install unknown apps** (optional) | Only for the in-app updater, to install Ultrebo's own update. Android rejects updates signed with a different key. |

## Community and support

Questions, bug reports or ideas? Join the [Discord server](https://discord.gg/mAKGfaAWWW) and open a support ticket there, or open an
[issue](../../issues) on GitHub.

## Quick start

1. **New macro**, open it.
2. Tap **Add step > Record inputs in the game** (or **Floating controls**), switch to your game, press **REC**, play the inputs you want, press **DONE**.
3. Back in Ultrebo, tap a step to edit it (position, delays, priority, repeat...). Use the ⋮ menu on a step to test, move or delete it.
4. Press **Start** at the bottom (or **RUN** on the floating bar). **STOP** ends it. Closing the bar (X) also stops it.

### Run modes

| Mode | Behaviour |
| --- | --- |
| **Sequence** | Runs every enabled step once per loop, lowest priority number first. Loops N times or forever, with a delay between loops. |
| **Reactive** | Each cycle, runs only the first step (lowest priority number) whose condition is met, then starts over. Tap/Swipe steps are always met, so give them the highest number to act as a fallback. A visible *Wait for image* step holds back every step below it. |

### Always-on image watching

Set a macro to **Reactive** mode with *Tap image* steps and it keeps watching the screen until you press STOP,
tapping whenever a target appears. *Check screen for images every (ms)* controls how often it looks:
`10000` checks every 10 seconds, which is much easier on the battery and CPU than the default 1 second.
The phone's screen has to stay on and Ultrebo's accessibility service has to stay enabled.

### Watchers: handling pop-ups while the macro runs

Turn on **Always watching** for an image step and it checks the screen in the background for the whole
run, even while your other steps are tapping. When its image appears (say an "I'm here" button):

- **Pause, then carry on** - the main macro pauses between taps, the watcher taps the image, waits the
  time you set, and the macro continues where it left off.
- **Restart macro from the start** - the macro is stopped, the watcher (optionally) taps the image,
  waits, then the macro starts again from step 1.

A macro with only watchers just sits and watches until you press STOP. Watchers need screen capture on.

### Text steps

Tap **Add step > Find text on screen**, type the words to find (for example `I'm here`), and use the *Tap it when found* switch to choose whether it taps or only waits.
It reads the screen with on-device OCR, so no picture needs to be cropped. Capital letters, spaces and
punctuation are ignored. *Match strictness* controls how many misread letters are forgiven (lower = more
forgiving). Text steps support everything image steps do, including **Always watching** and the two
pop-up actions. Reads Latin letters and numbers only (English and similar), and is slower than image
matching, so use a longer check interval (1-3 seconds) if the phone gets warm.

### Image steps

1. In a macro, tap **Add step > Find a picture on screen**, then press *Pick image from screen*.
2. Switch to your game, press **CROP**, and drag a box around the target (a button or icon).
3. Reopen Ultrebo - a thumbnail appears on the step. Raise the match threshold if it taps the wrong
   thing, lower it if it misses.

Tips: crop tightly around something distinctive; capture on the same phone and orientation you will run
on (matching is done at screen resolution); avoid regions with animated or changing numbers.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Accessibility option is greyed out | Settings > Apps > Ultrebo > ⋮ > *Allow restricted settings*, then retry. |
| Taps land in the wrong place | Re-record after changing screen rotation or display size; coordinates are absolute. |
| Image is never found | Re-crop it, lower the threshold (try 0.75), and make sure screen capture is ON. |
| Image taps the wrong thing | Crop a more distinctive area and raise the threshold (0.9+). |
| Macro stops by itself | Android may have turned the accessibility service off (battery savers do this). Re-enable it and exclude Ultrebo from battery optimisation. |
| Games detect and block it | Some games block gestures from accessibility services. Nothing here works around that. |

## Privacy

Macros and cropped images are stored in the app's private storage and never uploaded. Screen capture is
only used for matching images, and a captured frame is never saved except the region you crop.

The app has the `INTERNET` permission for two things:

1. On launch it asks GitHub's public API for the latest release of this repo and compares version numbers.
   Nothing about you, your phone or your macros is sent. Turn it off with the **Check for updates on
   launch** switch on the home screen.
2. Text recognition uses Google's [ML Kit](https://developers.google.com/ml-kit) library. Recognition itself
   runs entirely on your phone with a model bundled in the app, and your screen content is never uploaded.
   ML Kit may, however, send anonymous usage/performance statistics to Google. If you don't want that,
   don't use text steps (image steps don't use ML Kit).

### Updating

When a newer release exists, a popup offers **Update now**. It downloads the APK from this repo's GitHub
release (checked against the SHA-256 GitHub publishes) and hands it to Android's installer, which updates
Ultrebo in place - your macros and settings are kept. Android still asks you to confirm, and the first
time it asks you to allow Ultrebo to "install unknown apps". A **Release page** button is there as a manual
fallback.

This only works if every release is signed with the same key (see *Releasing* below). Android may switch
off the accessibility service after an update; the **Get started** guide reappears if so.

Because of this, the app declares the `REQUEST_INSTALL_PACKAGES` permission. It is only used for this
self-update and can only ever install an update to Ultrebo itself (Android rejects anything signed with a
different key).

## Building from source

```
./gradlew testDebugUnitTest assembleDebug
```
Open the folder in Android Studio, or let GitHub Actions build it: every push uploads a
`Ultrebo-debug-apk` artifact (sign-in required to download).

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
2. The *Release APK* workflow builds the app and attaches `Ultrebo-v1.0.0.apk` to that release.

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

[GPL-3.0](LICENSE), copyright (C) 2026 Ultrevo. Anyone may use, study and modify this app, but copies and
modified versions must stay open source under the same licence and keep the copyright notice. See
[NOTICE](NOTICE) for the extra permission covering Google ML Kit. Releases v0.1.0 to v0.1.2 were published
under the MIT License and remain available under those terms.
