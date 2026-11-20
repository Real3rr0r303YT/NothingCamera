# NothingCamera ●

**English** · [Deutsch](README.de.md) · [Türkçe](README.tr.md)

**A private, offline camera for Android in Nothing style.** Built as the system camera for
**NoxisOS** and shipped as a **KernelSU module**, but it runs on any Android 10+ phone.
OLED black, dot-matrix details, one red accent, and **no internet access at all**.

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-3rr0r303yt-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/3rr0r303yt)
![License: GPL v3](https://img.shields.io/badge/License-GPLv3-DA291C)
![No INTERNET permission](https://img.shields.io/badge/INTERNET-none-black)

## Features

- **Photo, video, night & portrait**: night and portrait modes appear automatically when the phone's
  camera extensions support them
- **Zoom up to 40×** with a **liquid-glass zoom slider** (1× → 40×, haptic ticks at 1 · 2 · 5 · 10 · 20 · 40, 0.6 bubble for ultra-wide) and pinch zoom: the camera's own zoom is used as far as it goes, beyond that
  the centre of the photo is cropped (digital, so quality drops at very high zoom). Plus **tap to focus** and **double-tap to switch camera**
- **Flash** (off / auto / on, torch while filming), **self-timer** (3 s / 10 s with a dot-matrix
  countdown), **grid**, **4:3 / 16:9**, **720p / 1080p / 4K at 30 or 60 fps** (quick button in video mode: HD 30 → HD 60 → FHD 30 → FHD 60 → 4K 30 → 4K 60; falls back to 30 fps if the camera can't do 60), **pause/resume** while recording
- **Volume and camera keys** work as the shutter
- **Icons rotate** with the phone while the UI stays put, and photos are always saved the right way up
- **Works as the system camera**: other apps can request photos and videos from it
  (`IMAGE_CAPTURE` / `VIDEO_CAPTURE`)
- **Home screen widget**: shutter plus photo / video buttons – opens straight in the right mode, shows no media
- **English, German and Turkish**: follows the system language, or pick one in Settings

## Privacy & OPSEC

| | |
|---|---|
| 🚫 **No network** | no `INTERNET` permission; it's explicitly stripped from the merged manifest, and CI fails if it ever appears. No ads, analytics, crash reporting or accounts. |
| 📍 **No location** | no location permissions at all (CI-checked), so a GPS position can never end up in a file. |
| 🧽 **Metadata always removed** | photos are captured **in memory** and every APPn/COM segment is cut out of the JPEG *before anything is written*: EXIF, GPS, make/model, serial numbers, timestamps, maker notes, embedded thumbnails, XMP, MPF/Ultra-HDR gain maps, and anything appended after the image. Only the colour profile and a fresh orientation tag are kept. The image data itself is not re-encoded, so there's no quality loss. |
| 🔐 **Encrypted vault** | optionally save to a private vault instead of the gallery. Every file gets its own AES-256 key, wrapped by a master key in the **Android Keystore** (TEE/hardware, usable only while the phone is unlocked). Content is sealed in 64 KiB AES-GCM chunks, so tampering, truncation and reordering are all detected. The vault isn't in the gallery or in any backups. |
| 🔒 **Vault lock** | fingerprint, face or screen-lock prompt to open; it locks again as soon as you leave the app |
| 💣 **Panic wipe** | *Wipe vault* destroys the Keystore key first (crypto-shredding), then overwrites and deletes the files |
| 🎲 **Random file names** | `IMG_3f9a1c…jpg` instead of a timestamp, so the name doesn't give away when you took it |
| 🖼️ **Screenshot block** | `FLAG_SECURE` (on by default): no screenshots or screen recordings, and a blank preview in Recents |
| 💾 **No backups** | `allowBackup=false` and data-extraction rules exclude everything, from cloud backup to device transfer |
| 🎤 **Mic only when needed** | `RECORD_AUDIO` is only requested when you film with sound; turn sound off and the microphone is never touched |
| 👁️ **No media read access** | the app never reads your gallery. It only writes its own files via MediaStore, with no storage permission |

### Permissions

| Permission | Why |
|---|---|
| `CAMERA` | taking photos and videos |
| `RECORD_AUDIO` (optional) | sound in videos |

That's the whole list.

## KernelSU module (NoxisOS)

Since Android 11, only **system** camera apps can answer other apps' "take a photo" requests. The
module installs NothingCamera as a system app, so it becomes the camera for every app.

1. Download **`nothingcamera-kernelsu-module`** from the latest
   [GitHub Actions run](../../actions) and unzip the artifact. Inside is `NothingCamera-vX.Y.Z-module.zip`.
2. **KernelSU** → *Modules* → *Install from storage* → choose the ZIP → reboot.
   (Magisk 20.4+ and APatch work too.)
3. Optional: in *Settings → Apps → Default apps* (or in the camera chooser), pick NothingCamera.

Building it yourself:

```bash
./gradlew assembleDebug
bash module/build.sh app/build/outputs/apk/debug/app-debug.apk app dist
# → dist/NothingCamera-v1.0.0-module.zip
```

The plain APK (`nothingcamera-debug-apk`) also installs normally without root. Without the module,
you only lose the "camera for other apps" part.

## Build

Requirements: JDK 17+, Android SDK 35.

```bash
chmod +x gradlew              # only if the executable bit got lost (e.g. web upload)
./gradlew testDebugUnitTest   # JPEG sanitizer, vault crypto and file-name tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/
```

Min SDK 29 (Android 10), target SDK 35. Kotlin 2.1, Jetpack Compose (Material 3), CameraX 1.4,
AndroidX Biometric. Every push runs on GitHub Actions: tests → APK → permission check → KernelSU module.

## Project structure

```
NothingCamera/
├── .github/workflows/android.yml      # tests, APK, privacy check, KernelSU module
├── module/                            # KernelSU / Magisk / APatch module (system app)
└── app/src/
    ├── main/java/com/real3rr0r303yt/nothingcamera/
    │   ├── core/        JpegSanitizer · VaultCrypto · FileNames   (pure Kotlin, unit tested)
    │   ├── privacy/     Vault · KeystoreKeyWrapper · MediaSaver
    │   ├── camera/      CameraController (CameraX: photo, video, extensions, zoom, focus)
    │   ├── data/        Settings
    │   └── ui/          camera · vault · settings · components (dot-matrix) · theme
    ├── main/res/        values (EN) · values-de · values-tr
    └── test/            JpegSanitizerTest · VaultCryptoTest · FileNamesTest
```

## Support

NothingCamera is free and has no ads. If you like it:
**[☕ Buy Me a Coffee: buymeacoffee.com/3rr0r303yt](https://buymeacoffee.com/3rr0r303yt)**
(also in the app under *Settings → Support*)

## License

Copyright © 2026 **Real3rr0r303YT**

This program is free software: you can redistribute it and/or modify it under the terms of the
**GNU General Public License v3.0** as published by the Free Software Foundation, either version 3
of the License, or (at your option) any later version. See [LICENSE](LICENSE).

NothingCamera is an unofficial fan project and is not affiliated with Nothing Technology Limited.

---

<p align="center"><b>Made by 3rr0r303YT on TikTok</b><br>
<a href="https://github.com/Real3rr0r303YT">GitHub: Real3rr0r303YT</a> · <a href="https://buymeacoffee.com/3rr0r303yt">☕ Buy Me a Coffee</a></p>
