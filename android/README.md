# Uçuş Modu — Android

`ucus-modu.html` içindeki rota tabanlı pomodoro sayacının native Android sürümü:
Kotlin + Jetpack Compose, tek ekran, çevrimdışı çalışır.

## Derleme

Android SDK gerektirir (bu depoda SDK yok, `local.properties` sizin makinenizde oluşur).

```bash
# Android Studio ile: android/ klasörünü aç, Run
# Komut satırıyla:
cd android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # bağlı cihaza kur
```

- `minSdk 26`, `targetSdk 35`, JDK 17, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.

## Web sürümünden ne taşındı

| Web | Android |
|---|---|
| Canvas'ta ortografik küre, paralel/meridyen ağı, büyük daire rotası | `ui/Globe.kt` — Compose `Canvas`, aynı izdüşüm ve aynı geometri |
| `localStorage` | `data/Store.kt` — SharedPreferences üzerinde JSON |
| `requestAnimationFrame` sayacı | `timer/FlightEngine.kt` — tek otorite, `StateFlow` |
| WebAudio kabin sesi | `audio/Chime.kt` — aynı notalar `AudioTrack` ile sentezleniyor |
| Sekme başlığındaki geri sayım | Foreground service bildirimi (Beklet / Atla eylemleriyle) |
| Barlow · Barlow Condensed · IBM Plex Mono (CDN) | Aynı fontlar `res/font` içinde gömülü |

Havalimanı tablosu, ön tanımlı rotalar, 850 km/pomodoro ölçeği, `TKxxxx` uçuş
numarası üretimi ve 4 pomodoroda bir uzun mola döngüsü web sürümüyle birebir
aynıdır; sayısal çıktılar orijinal JavaScript'e karşı doğrulanmıştır.

## Uygulama akışı

Gerçek bir uçuş günü gibi üç adım:

1. **Terminal** (`ui/WelcomeScreen.kt`) — saat, küre, toplam istatistikler ve
   **Yolculuğa başla**. Yarım kalmış bir rota varsa doğrudan devam edilebilir.
2. **Rota seç** (`ui/RouteScreen.kt`) — hazır rotalar ya da kendi rotan; seçim
   anında küreye yansır. Biniş kartında uçuş no, kapı, kalkış ve tahmini varış
   saati, odakta geçecek süre ve gerçek uçuş süresi yazar. **Uçuşa başlat**.
3. **Uçuş** (`ui/FlightScreen.kt`) — küre, sayaç, uçuş planı, ayarlar ve günlük.
   Üstteki **Rota** düğmesi ikinci adıma döner.

Geri tuşu bu zinciri tersten yürür; sayaç hangi ekranda olursanız olun işlemeye
devam eder.

## Küre

Kıyı şeritleri Natural Earth'ün kamu malı verisinden geliyor; `tools/build_coastline.py`
GeoJSON'u kompakt bir ikili biçime çeviriyor (int16 kuantalama, yeryüzünde en fazla
~340 m sapma):

| Varlık | Kaynak | Boyut | Ne zaman |
|---|---|---|---|
| `coastline_110m.bin` | ne_110m_coastline | 21 KB | her zaman |
| `coastline_50m.bin` | ne_50m_coastline | 242 KB | yakınlaştırınca (zoom ≥ 2,2) |

Noktalar birim küre üzerinde (x, y, z) olarak saklanıyor; böylece her karede
trigonometri yerine yalnızca iç çarpım hesaplanıyor, arkada kalan parçalar da
tek bir karşılaştırmayla eleniyor.

Rota ekranındaki küre sürüklenerek döndürülür, çimdiklenerek yakınlaştırılır,
çift dokunuşla yaklaşır; **Rotaya dön** düğmesi kamerayı rotanın merkezine
geri getirir. Diğer ekranlarda küre etkileşimsizdir ve rotayı takip eder.

Varlıkları yeniden üretmek için:

```bash
python3 tools/build_coastline.py ne_110m_coastline.geojson \
    android/app/src/main/assets/coastline_110m.bin
```

## Native olarak eklenenler

- **Arka planda doğru sayaç:** süre duvar saatinden (`endAt`) türetilir, ekran
  kapalıyken faz bitişini `AlarmManager` ile kesin alarm yakalar; uygulama
  öldürülse bile durum diskten geri kurulur.
- **Bildirimler:** seyir sırasında kalan süreyi gösteren kalıcı bildirim,
  faz bitiminde kabin anonsu (Android 13+ için bildirim izni istenir).
- **Açık/koyu tema:** sistem temasını izler, renkler web sürümündeki CSS
  değişkenlerinin birebir karşılığıdır.

## Bilinen sınırlar

- Bu depo Android SDK'sız bir ortamda hazırlandı; APK derlemesi ilk kez
  sizin makinenizde çalışacak. Saf mantık katmanı (geometri, rota, durum,
  biçimlendirme) JVM'de derlenip orijinal JavaScript çıktısıyla karşılaştırıldı,
  Compose/servis katmanı ise derlenmedi.
- Servis `specialUse` foreground service tipi kullanır; Play Store'a yüklenecekse
  bu tip için gerekçe girmek gerekir.
