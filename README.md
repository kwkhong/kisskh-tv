# KissKH TV

Android TV / Chromecast with Google TV browser app opening **https://kisskh.co/**.
Based on the prepared `kisskh-tv-ready.zip` project.

## Download the APK

1. Open [Actions → Android TV APK](https://github.com/kwkhong/kisskh-tv/actions/workflows/android-ci.yml).
2. Open the latest **successful main** run.
3. In **Artifacts**, download **kisskh-tv-apk**.
4. Unzip it. The installable file is **kisskh-tv.apk**; the other file is its SHA-256 checksum.

Artifacts are retained for 90 days. Run the workflow again to generate a new download.
This is a debug-signed APK for personal sideloading, not a Play Store release.
Different fresh builds may use different debug certificates; if Android reports an
incompatible update, uninstall the old build first (this clears saved website data).

## Install

Transfer `kisskh-tv.apk` to your Chromecast with Google TV or Android TV device.
Allow your chosen file-transfer/file-manager app to install unknown apps, open the
APK, and select **Install**. Launch **KissKH TV** from the TV Apps screen.

With an already connected ADB device: `adb install -r kisskh-tv.apk`.
This requires a Chromecast **with Google TV**, not a cast-only Chromecast dongle.

## Remote controls

| Control | Action |
| --- | --- |
| D-pad | Move visible focus between links, buttons, search fields and videos |
| OK / Enter | Select the focused control using a normal browser touch event |
| Hold OK for at least one second | Toggle pointer mode for embedded player controls |
| D-pad in pointer mode | Move the pointer; hold a direction to move faster |
| OK in pointer mode | Click the control under the pointer |
| Back | Exit fullscreen, otherwise leave pointer mode, otherwise go back a page, otherwise close |

Select the website player's fullscreen button to enter fullscreen. Pointer mode
can reach controls inside cross-origin frames using normal touch interaction.
The on-screen keyboard keeps Android's normal D-pad handling.

## Features and scope

- TV launcher icon/banner, landscape display, Android 6.0+ (API 23), hardware acceleration.
- JavaScript, DOM storage, and cookies including third-party cookies for ordinary embedded playback.
- HTML5 fullscreen via Android WebChromeClient; immersive system bars and correct Back handling.
- Main-page network/HTTP error screen with remote-focusable Retry.
- HTTPS only; certificate errors fail closed; no file/content access, arbitrary external
  intents, downloads, automatic/nested popups, camera/microphone grants, or native JavaScript bridge.
- No DRM bypass, scraping, downloading, access-control bypass, or video re-hosting.

The website and its providers control availability, login, codec and WebView compatibility.
Passing automated tests does not guarantee playback from every third-party media host.
If a provider refuses WebView or requires unsupported DRM, the app does not bypass it.

## Sign-in windows (1.0.1)

User-initiated popup windows now keep their opener alive and support D-pad, OK,
pointer mode and Back to close. A native header shows the popup host. Automatic
and nested popups remain blocked.

**This does not enable Google account login inside the APK.** Google account
navigation is intercepted before loading and explains the restriction, with an
optional external-browser action that opens KissKH from the beginning. The browser
has its own session: it does not log the APK in. The known Firebase popup-closed
alert is acknowledged only after this explanation; other website alerts remain.
No user-agent spoofing, cookie transfer or authentication bypass is used.

## Build and test

Use JDK 17, Android SDK platform 35, build tools 35.0.0. AGP 8.9.1,
Kotlin 2.1.20 and Gradle 8.11.1 are pinned; the Gradle wrapper validates its download checksum.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
# With an Android TV emulator or device connected:
./gradlew connectedDebugAndroidTest
```

Local APK: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions runs JVM URL-policy tests, Android lint, compilation, and Android TV
API 30 emulator tests covering launcher/settings, D-pad/OK, pointer mode, actual
HTML5 video playback/fullscreen/Back with an original generated test clip, network
failure/Retry focus, and rejected unsafe navigation. The APK is uploaded only after
all gates pass. Test reports are uploaded even on failure as **android-test-reports**.
A physical Chromecast and live provider playback still need on-device verification.
