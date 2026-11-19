# NothingCamera ●

[English](README.md) · [Deutsch](README.de.md) · **Türkçe**

**Android için Nothing tarzında, gizli ve çevrimdışı bir kamera.** **NoxisOS**'un sistem kamerası
olarak yapıldı ve **KernelSU modülü** olarak geliyor, ama Android 10+ olan her telefonda çalışır.
OLED siyahı, nokta matris detaylar, tek bir kırmızı vurgu ve **hiç internet erişimi yok**.

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-3rr0r303yt-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/3rr0r303yt)
![Lisans: GPL v3](https://img.shields.io/badge/Lisans-GPLv3-DA291C)

## Özellikler

- **Fotoğraf, video, gece ve portre**: telefonun kamera eklentileri destekliyorsa gece ve portre
  modları otomatik olarak görünür
- **Liquid glass yakınlaştırma kaydırıcısıyla 40×'e kadar zoom** (1× → 40×, 1 · 2 · 5 · 10 · 20 · 40'ta titreşimli duraklar, ultra geniş için 0,6 düğmesi) ve iki parmakla yakınlaştırma: kameranın kendi zoom'u
  gidebildiği yere kadar kullanılır, ötesinde fotoğrafın ortası kırpılır (dijital, yani çok yüksek zoom'da kalite düşer).
  Ayrıca **odaklamak için dokun** ve **kamerayı değiştirmek için çift dokun**
- **Flaş** (kapalı / otomatik / açık, video çekerken fener), **zamanlayıcı** (3 sn / 10 sn, nokta
  matris geri sayım), **ızgara**, **4:3 / 16:9**, **30 veya 60 fps ile 720p / 1080p / 4K** (video modunda hızlı düğme: HD 30 → HD 60 → FHD 30 → FHD 60 → 4K 30 → 4K 60; kamera 60 yapamıyorsa 30 fps'e düşer), kayıt sırasında **duraklat**
- **Ses ve kamera tuşları** deklanşör olarak çalışır
- **Simgeler telefonla birlikte döner**, arayüz sabit kalır ve fotoğraflar her zaman doğru yönde kaydedilir
- **Sistem kamerası olarak çalışır**: diğer uygulamalar ondan fotoğraf ve video isteyebilir
  (`IMAGE_CAPTURE` / `VIDEO_CAPTURE`)
- **Türkçe, İngilizce, Almanca**: sistem dilini izler veya Ayarlar'dan seçilir

## Gizlilik ve OPSEC

| | |
|---|---|
| 🚫 **Ağ yok** | `INTERNET` izni yok; birleştirilmiş manifest'ten açıkça çıkarılır ve bir gün eklenirse CI başarısız olur. Reklam, analiz, çökme raporu veya hesap yok. |
| 📍 **Konum yok** | hiçbir konum izni yok (CI tarafından kontrol edilir), bu yüzden bir GPS konumu asla bir dosyaya giremez. |
| 🧽 **Meta veriler her zaman silinir** | fotoğraflar **bellekte** çekilir ve *herhangi bir şey yazılmadan önce* JPEG'deki her APPn/COM segmenti kesilir: EXIF, GPS, marka/model, seri numaraları, zaman damgaları, üretici notları, gömülü küçük resimler, XMP, MPF/Ultra-HDR kazanç haritaları ve görüntünün arkasına eklenmiş her şey. Yalnızca renk profili ve yeni bir yön etiketi kalır. Görüntü verisinin kendisi yeniden kodlanmaz, yani kalite kaybı olmaz. |
| 🔐 **Şifreli kasa** | istersen galeri yerine gizli bir kasaya kaydet. Her dosya kendi AES-256 anahtarını alır; bu anahtar **Android Keystore**'daki bir ana anahtarla sarılır (TEE/donanım, yalnızca telefon kilitli değilken kullanılabilir). İçerik 64 KiB'lık AES-GCM parçalarıyla mühürlenir, bu yüzden değiştirme, kesme ve yeniden sıralama fark edilir. Kasa galeride de yedeklerde de yoktur. |
| 🔒 **Kasa kilidi** | açmak için parmak izi, yüz veya ekran kilidi; uygulamadan çıktığın anda tekrar kilitlenir |
| 💣 **Panik silme** | *Kasayı sil* önce Keystore anahtarını yok eder (kripto imha), sonra dosyaların üzerine yazıp siler |
| 🎲 **Rastgele dosya adları** | zaman damgası yerine `IMG_3f9a1c…jpg`, böylece ad fotoğrafı ne zaman çektiğini ele vermez |
| 🖼️ **Ekran görüntüsü engeli** | `FLAG_SECURE` (varsayılan olarak açık): ekran görüntüsü veya ekran kaydı yok, Son uygulamalar'da boş önizleme |
| 💾 **Yedekleme yok** | `allowBackup=false` ve veri çıkarma kuralları, bulut yedeklemesinden cihaz aktarımına kadar her şeyi hariç tutar |
| 🎤 **Mikrofon yalnızca gerektiğinde** | `RECORD_AUDIO` yalnızca sesli video çektiğinde istenir; ses kapalıyken mikrofona hiç dokunulmaz |
| 👁️ **Medya okuma erişimi yok** | uygulama galerini asla okumaz. Yalnızca kendi dosyalarını MediaStore üzerinden yazar, depolama izni gerekmez |

### İzinler

| İzin | Neden |
|---|---|
| `CAMERA` | fotoğraf ve video çekmek |
| `RECORD_AUDIO` (isteğe bağlı) | videolarda ses |

Hepsi bu kadar.

## KernelSU modülü (NoxisOS)

Android 11'den beri diğer uygulamaların "fotoğraf çek" isteklerine yalnızca **sistem** kamera
uygulamaları yanıt verebilir. Modül, NothingCamera'yı sistem uygulaması olarak kurar; böylece tüm
uygulamaların kamerası olur.

1. En son [GitHub Actions çalışmasından](../../actions) **`nothingcamera-kernelsu-module`**
   dosyasını indir ve artefaktı aç. İçinde `NothingCamera-vX.Y.Z-module.zip` var.
2. **KernelSU** → *Modüller* → *Depolamadan yükle* → ZIP'i seç → yeniden başlat.
   (Magisk 20.4+ ve APatch de çalışır.)
3. İsteğe bağlı: *Ayarlar → Uygulamalar → Varsayılan uygulamalar* bölümünden (veya kamera seçicide) NothingCamera'yı seç.

Kendin derlemek için:

```bash
./gradlew assembleDebug
bash module/build.sh app/build/outputs/apk/debug/app-debug.apk app dist
# → dist/NothingCamera-v1.0.0-module.zip
```

Normal APK (`nothingcamera-debug-apk`) root olmadan da kurulabilir. Modül olmadan yalnızca
"diğer uygulamalar için kamera" kısmı eksik kalır.

## Derleme

Gereksinimler: JDK 17+, Android SDK 35.

```bash
chmod +x gradlew              # yalnızca çalıştırma biti kaybolduysa (ör. web yüklemesi)
./gradlew testDebugUnitTest   # JPEG temizleyici, kasa şifrelemesi ve dosya adı testleri
./gradlew assembleDebug       # app/build/outputs/apk/debug/
```

Min SDK 29 (Android 10), hedef SDK 35. Kotlin 2.1, Jetpack Compose (Material 3), CameraX 1.4,
AndroidX Biometric. Her push GitHub Actions'ta çalışır: testler → APK → izin kontrolü → KernelSU modülü.

## Destek

NothingCamera ücretsiz ve reklamsızdır. Beğendiysen:
**[☕ Buy Me a Coffee: buymeacoffee.com/3rr0r303yt](https://buymeacoffee.com/3rr0r303yt)**
(uygulamada da *Ayarlar → Destek* altında)

## Lisans

Telif hakkı © 2026 **Real3rr0r303YT**

Bu program özgür yazılımdır: Free Software Foundation tarafından yayımlanan **GNU Genel Kamu Lisansı
v3.0**'ın (lisansın 3. sürümü veya tercihine göre daha sonraki herhangi bir sürümü) koşulları
altında yeniden dağıtabilir ve/veya değiştirebilirsin. Bkz. [LICENSE](LICENSE).

NothingCamera resmi olmayan bir hayran projesidir ve Nothing Technology Limited ile bağlantılı değildir.

---

<p align="center"><b>Made by 3rr0r303YT on TikTok</b><br>
<a href="https://github.com/Real3rr0r303YT">GitHub: Real3rr0r303YT</a> · <a href="https://buymeacoffee.com/3rr0r303yt">☕ Buy Me a Coffee</a></p>
