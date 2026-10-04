# Maintaining Ultrebo (Android)

Notes for the maintainer. Not needed to use the app.

## Building

```
./gradlew testDebugUnitTest assembleDebug
```

Open the folder in Android Studio, or let GitHub Actions build it: every push uploads an `Ultrebo-debug-apk`
artifact (sign-in required to download).

```
app/src/main/java/com/ultimate/macrobot/
  model/    Macro, Step, enums (JSON-serialised)
  data/     MacroRepository (macros.json + template PNGs), update checker and installer
  engine/   MacroRunner (sequence/reactive loop and rules), ImageMatcher (OpenCV), text matching
  service/  MacroAccessibilityService (gestures + overlays), OverlayController, ScreenCaptureService
  ui/       Compose screens: Home, Editor, StepDialog, SetupGuide
docs/       The website (GitHub Pages)
```

## Releasing

1. On GitHub: **Releases > Draft a new release**, create a tag such as `v0.1.5`, **Publish**.
2. The *Release APK* workflow builds the app and attaches `Ultrebo-v0.1.5.apk` to that release. The tag becomes the version.

## Signing

Android only installs an update over an existing install if both APKs have the same signing key. The key is
kept as repository secrets (Settings > Secrets and variables > Actions): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`. Keep the keystore and passwords backed up and **never commit them**. Without the
secrets the workflow still builds, but signs with the debug key, so users would have to uninstall before moving
to a properly signed build.

To make a new key once:

```
keytool -genkeypair -v -keystore release.keystore -alias macrobot -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 release.keystore      # paste as KEYSTORE_BASE64
```
