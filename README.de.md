# NothingCamera ●

[English](README.md) · **Deutsch** · [Türkçe](README.tr.md)

**Private Offline-Kamera für Android im Nothing-Look.** Gebaut als Systemkamera für **NoxisOS**
und ausgeliefert als **KernelSU-Modul**, läuft aber auf jedem Android-10+-Handy.
Tiefschwarz, Dot-Matrix-Details, ein roter Akzent und **überhaupt kein Internetzugriff**.

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-3rr0r303yt-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/3rr0r303yt)
![Lizenz: GPL v3](https://img.shields.io/badge/Lizenz-GPLv3-DA291C)

## Funktionen

- **Foto, Video, Nacht & Porträt**: Nacht- und Porträtmodus erscheinen automatisch, wenn die
  Kamera-Extensions des Handys sie unterstützen
- **Zoom bis 40×** mit **Liquid-Glass-Zoomregler** (1× → 40×, spürbare Raster bei 1 · 2 · 5 · 10 · 20 · 40, 0,6-Knopf für Ultraweitwinkel) und Pinch-Zoom: der Zoom der Kamera wird so weit wie möglich genutzt, darüber
  wird die Fotomitte zugeschnitten (digital, also schlechtere Qualität bei sehr starkem Zoom). Dazu **Tippen zum Fokussieren**
  und **Doppeltippen zum Kamerawechsel**
- **Blitz** (aus / auto / an, beim Filmen als Taschenlampe), **Selbstauslöser** (3 s / 10 s mit
  Dot-Matrix-Countdown), **Raster**, **4:3 / 16:9**, **720p / 1080p / 4K mit 30 oder 60 fps** (Schnellknopf im Videomodus: HD 30 → HD 60 → FHD 30 → FHD 60 → 4K 30 → 4K 60; fällt auf 30 fps zurück, wenn die Kamera keine 60 kann), **Pause** beim Filmen
- **Lautstärke- und Kamerataste** lösen aus
- **Icons drehen sich** mit dem Handy, die Oberfläche bleibt stehen, Fotos werden immer richtig herum gespeichert
- **Funktioniert als Systemkamera**: andere Apps können Fotos und Videos anfordern
  (`IMAGE_CAPTURE` / `VIDEO_CAPTURE`)
- **Deutsch, Englisch, Türkisch**: folgt der Systemsprache oder wird in den Einstellungen gewählt

## Datenschutz & OPSEC

| | |
|---|---|
| 🚫 **Kein Netz** | keine `INTERNET`-Berechtigung; sie wird aktiv aus dem Manifest entfernt, und die CI schlägt fehl, falls sie je auftaucht. Keine Werbung, kein Tracking, keine Crash-Reports, keine Konten. |
| 📍 **Kein Standort** | überhaupt keine Standort-Berechtigungen (von der CI geprüft), also kann nie eine GPS-Position in einer Datei landen. |
| 🧽 **Metadaten immer weg** | Fotos werden **im Arbeitsspeicher** aufgenommen, und jedes APPn/COM-Segment wird aus dem JPEG geschnitten, *bevor irgendetwas gespeichert wird*: EXIF, GPS, Hersteller/Modell, Seriennummern, Zeitstempel, Maker Notes, eingebettete Vorschaubilder, XMP, MPF/Ultra-HDR-Gainmaps und alles, was hinter dem Bild hängt. Nur das Farbprofil und ein frisches Orientierungs-Tag bleiben. Die Bilddaten selbst werden nicht neu kodiert, also gibt es keinen Qualitätsverlust. |
| 🔐 **Verschlüsselter Tresor** | wahlweise Speichern in einen privaten Tresor statt in die Galerie. Jede Datei bekommt einen eigenen AES-256-Schlüssel, der mit einem Master-Key im **Android Keystore** verpackt ist (TEE/Hardware, nur nutzbar, solange das Handy entsperrt ist). Der Inhalt wird in 64-KiB-AES-GCM-Blöcken versiegelt, Manipulation, Kürzen und Umsortieren fallen also auf. Der Tresor ist weder in der Galerie noch in Backups. |
| 🔒 **Tresorsperre** | Öffnen per Fingerabdruck, Gesicht oder Displaysperre; sperrt sich wieder, sobald du die App verlässt |
| 💣 **Panik-Löschen** | *Tresor löschen* vernichtet zuerst den Keystore-Schlüssel (Crypto-Shredding), überschreibt dann die Dateien und löscht sie |
| 🎲 **Zufällige Dateinamen** | `IMG_3f9a1c…jpg` statt Zeitstempel, damit der Name nicht verrät, wann du fotografiert hast |
| 🖼️ **Screenshot-Sperre** | `FLAG_SECURE` (standardmäßig an): keine Screenshots, keine Bildschirmaufnahme, leere Vorschau in den letzten Apps |
| 💾 **Keine Backups** | `allowBackup=false` und Data-Extraction-Regeln schließen alles aus, von Cloud-Backup bis Geräteumzug |
| 🎤 **Mikrofon nur bei Bedarf** | `RECORD_AUDIO` wird erst abgefragt, wenn du mit Ton filmst; mit Ton aus wird das Mikrofon nie angefasst |
| 👁️ **Kein Lesezugriff auf Medien** | die App liest deine Galerie nie. Sie schreibt nur ihre eigenen Dateien über MediaStore, ganz ohne Speicher-Berechtigung |

### Berechtigungen

| Berechtigung | Wofür |
|---|---|
| `CAMERA` | Fotos und Videos aufnehmen |
| `RECORD_AUDIO` (optional) | Ton in Videos |

Mehr gibt es nicht.

## KernelSU-Modul (NoxisOS)

Seit Android 11 dürfen nur **System**-Kamera-Apps auf „Foto aufnehmen"-Anfragen anderer Apps
antworten. Das Modul installiert NothingCamera als System-App, damit sie die Kamera für alle Apps wird.

1. Aus dem neuesten [GitHub-Actions-Lauf](../../actions) **`nothingcamera-kernelsu-module`**
   herunterladen und das Artefakt entpacken. Darin liegt `NothingCamera-vX.Y.Z-module.zip`.
2. **KernelSU** → *Module* → *Aus Speicher installieren* → ZIP wählen → neu starten.
   (Magisk 20.4+ und APatch gehen auch.)
3. Optional: unter *Einstellungen → Apps → Standard-Apps* (oder in der Kamera-Auswahl) NothingCamera wählen.

Selbst bauen:

```bash
./gradlew assembleDebug
bash module/build.sh app/build/outputs/apk/debug/app-debug.apk app dist
# → dist/NothingCamera-v1.0.0-module.zip
```

Die normale APK (`nothingcamera-debug-apk`) lässt sich auch ganz ohne Root installieren. Ohne Modul
fehlt dann nur der Teil „Kamera für andere Apps".

## Bauen

Voraussetzungen: JDK 17+, Android SDK 35.

```bash
chmod +x gradlew              # nur nötig, wenn das Ausführbar-Bit verloren ging (z. B. Web-Upload)
./gradlew testDebugUnitTest   # Tests für JPEG-Sanitizer, Tresor-Krypto und Dateinamen
./gradlew assembleDebug       # app/build/outputs/apk/debug/
```

Min SDK 29 (Android 10), Target SDK 35. Kotlin 2.1, Jetpack Compose (Material 3), CameraX 1.4,
AndroidX Biometric. Jeder Push läuft durch GitHub Actions: Tests → APK → Berechtigungs-Check → KernelSU-Modul.

## Unterstützen

NothingCamera ist kostenlos und werbefrei. Wenn sie dir gefällt:
**[☕ Buy Me a Coffee: buymeacoffee.com/3rr0r303yt](https://buymeacoffee.com/3rr0r303yt)**
(auch in der App unter *Einstellungen → Unterstützen*)

## Lizenz

Copyright © 2026 **Real3rr0r303YT**

Dieses Programm ist freie Software: Du kannst es unter den Bedingungen der **GNU General Public
License v3.0**, wie von der Free Software Foundation veröffentlicht, weitergeben und/oder ändern,
entweder gemäß Version 3 der Lizenz oder (nach deiner Wahl) jeder späteren Version. Siehe [LICENSE](LICENSE).

NothingCamera ist ein inoffizielles Fanprojekt und steht in keiner Verbindung zu Nothing Technology Limited.

---

<p align="center"><b>Made by 3rr0r303YT on TikTok</b><br>
<a href="https://github.com/Real3rr0r303YT">GitHub: Real3rr0r303YT</a> · <a href="https://buymeacoffee.com/3rr0r303yt">☕ Buy Me a Coffee</a></p>
