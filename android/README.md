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
2. **Rota seç** (`ui/RouteScreen.kt`) — küreyi çevirip şehre dokunarak ya da
   arama kutusundan kalkış ve varış seçilir; hazır rotalar da duruyor. Biniş
   kartında uçuş no, kapı, kalkış ve tahmini varış saati, odakta geçecek süre
   ve gerçek uçuş süresi yazar. **Uçuşa başlat**.
3. **Bilet** (`ui/TicketScreen.kt`) — "Bilet kes" denince biniş kartı yazıcı
   yuvasından duraklayarak beslenir, sonra **parmakla koparılır**: perforasyon
   üzerinde sağa kaydırdıkça yırtık ilerler, koparılan parça açıyla sarkar ve
   her tırtıkta kısa bir titreşim gelir. Yarıdan azken bırakılırsa geri yapışır.
   Uçuş ancak bilet koparılınca başlayabilir.
   Koçanda havayolu ve rota, koparılan parçada yolcu adı, uçuş numarası, kapı,
   koltuk, biniş/kalkış/varış saatleri, mesafe ve dekoratif barkod var. Koltuk
   rotaya ve güne sabitlenmiştir: aynı gün aynı rota aynı koltuğu verir.
4. **Uçuş** (`ui/FlightScreen.kt`) — küre, sayaç, uçuş planı, ayarlar ve günlük.
   Üstteki **Rota** düğmesi ikinci adıma döner. Yolcu adı buradaki ayarlardan
   giriliyor; boş bırakılırsa bilette "YOLCU" yazar.

Geri tuşu bu zinciri tersten yürür; sayaç hangi ekranda olursanız olun işlemeye
devam eder.

## Küre

Kara parçaları Natural Earth'ün kamu malı verisinden geliyor; `tools/build_shapes.py`
GeoJSON'u kompakt bir ikili biçime çeviriyor (int16 kuantalama, yeryüzünde en fazla
~340 m sapma):

| Varlık | Kaynak | Boyut | Ne zaman |
|---|---|---|---|
| `land_110m.bin` | ne_110m_land | 21 KB | her zaman |
| `land_50m.bin` | ne_50m_land | 243 KB | yakınlaştırınca (zoom ≥ 2,2) |
| `lakes_110m.bin` | ne_110m_lakes | 2 KB | her zaman |

Kara poligonları hem dolgu hem kıyı çizgisi için kullanılıyor. Dolguda küre
arkasına düşen noktalar ufka yapıştırılıp aradaki boşluk ufuk boyunca
dolduruluyor; böylece kenardan taşan kıtaların silueti ufku takip ediyor,
delikler de even-odd kuralıyla açılıyor. Kıyı çizgisi yalnızca görünen
parçalardan çiziliyor, ufuk boyunca çizgi çekilmiyor.

Noktalar birim küre üzerinde (x, y, z) olarak saklanıyor; böylece her karede
trigonometri yerine yalnızca iç çarpım hesaplanıyor, arkada kalan parçalar da
tek bir karşılaştırmayla eleniyor.

Rota ekranında küre kaydırılabilir alanın dışında, sabit bir bölgede durur —
aksi halde sürükleme jestini sayfa kaydırma kapar ve küre dönmez.

- **Döndürme:** sürükleme
- **Yakınlaştırma:** kürenin kenarındaki +/− düğmeleri ve dikey zoom kolu,
  çimdikleme, fare tekerleği — ölçek 0,55×–18×.
  - Zoom kolu fareyle de sürüklenebildiği için çimdiklemenin çalışmadığı
    ortamlarda (emülatör, masaüstü) sürekli yakınlaştırmanın garanti yolu.
    Ölçek logaritmik dağıtılır: her piksel eşit oranda büyütür.
  - Emülatörde çimdikleme için Ctrl basılı tutup sürükleyin.
  - Tekerlek olayı `PointerEventPass.Initial` aşamasında yakalanıyor, ama
    emülatör fareyi parmak gibi taklit ettiği için olayı hiç iletmeyebilir.
- **Çift dokunuş:** dokunulan noktaya yaklaşır
- **Rotaya dön:** kamerayı rotanın merkezine geri getirir

Çimdikleme ve tekerlek, parmağın altındaki coğrafi noktayı yerinde tutar.
Bunun için yaklaşım yapmak yerine kamera doğrudan çözülüyor: nokta kamera
tabanında (doğu, kuzey, yukarı) bileşenlerine ayrılıp enlem tek bir sinüs
denkleminden, boylam da yatay bileşenin dönme açısından bulunuyor. Denemede
her ölçekte kayma tam olarak sıfır piksel çıktı.

- **Atalet:** parmak kalkınca küre sönümlenerek dönmeye devam eder
- **Gündüz/gece:** dünyanın karanlık yarısı gölgelenir. Alt-güneş noktası
  `data/Sun.kt` ile hesaplanıyor (21 Haziran +23,44°, 21 Aralık −23,43°,
  ekinoksta ekvator — astronomiyle doğrulandı). Gece bölgesinin sınırı
  terminatörün görünen yayı ile onu kapatan ufuk yayından kuruluyor; poligon
  18.000 noktada gerçekle karşılaştırıldı, tek sapma ufuk çizgisinin dibindeki
  kiriş artefaktıydı, o da yayı %2 taşırıp küreye kırpılarak giderildi.
- **İzler:** tamamlanan uçuşlar kürede soluk çizgiler olarak kalır.

Terminal ekranında toplam yolun kaç **dünya turu** ettiği de gösteriliyor
(40.075 km bir tur). Faz değişimlerinde kısa bir titreşim veriliyor; ses
kapalıyken de çalışır, kabin anonsunun sessiz karşılığıdır.

Diğer ekranlarda küre etkileşimsizdir ve rotayı takip eder.

## Yerler

Uçuş yapılabilen yerler OurAirports'un kamu malı verisinden geliyor:
tarifeli seferi, IATA kodu ve şehir adı olan **3.941 havalimanı / 3.858 şehir**.
`tools/build_places.py` bunu 137 KB'lık `places.bin` dosyasına indiriyor
(`assets/places.bin`, biçim `UMP2`).

- Küre yakınlaştıkça daha küçük havalimanları beliriyor (uzakta yalnızca büyük
  havalimanları, zoom ≥ 1,7'de orta, ≥ 3'te küçük olanlar).
- Arama şehir adını, havalimanı adını ve IATA kodunu birlikte tarıyor; Türkçe
  büyük/küçük harf ve aksan farkları normalleştiriliyor ("izmir" → İzmir).
- `data/TurkishCities.kt` veri setindeki adları Türkçeye çeviriyor (Munich →
  Münih, London → Londra) ve OurAirports'un ilçe yazdığı yerleri düzeltiyor
  (Gaziemir → İzmir, "Pendik, Istanbul" → İstanbul).
- Ülke adları Android'in kendi yerelleştirmesinden geliyor (`countryName`).

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

## Testler

```bash
cd android
./gradlew test          # 47 birim test, JVM'de çalışır, cihaz gerekmez
```

`app/src/test/` altında saf mantık katmanının tamamı sınanıyor: küre
geometrisi (web sürümünün ürettiği sayılara karşı), rota kurulumu ve uçuş
numarası üretimi, uçuş süresi ve mola takvimi hesapları, Türkçe biçimlendirme,
güneş konumu (gündönümü ve ekinokslara karşı), durum türevleri (ilerleme oranı,
irtifa, kalan bacak), ikili varlık biçimleri ve Türkçe duyarlı arama
(Gaziemir → İzmir, Munich → Münih) ve bilet üretimi (koltuk kararlılığı,
saat sıralaması, barkod deseni).

## Derlemeden önce

Depoda Android SDK olmadığı için Compose katmanı burada derlenemiyor.
`tools/lint_kotlin.py` derleyici olmadan yakalanabilen üç hata sınıfını tarar:
dengesiz ayraçlar, tanımsız tür/fonksiyon adları ve JVM imza çakışmaları
(`var zoom` ile `fun setZoom` aynı imzayı üretir).

```bash
python3 tools/lint_kotlin.py android/app/src/main/java
```

Saf mantık katmanı (geometri, rota, biçimlendirme, yer ve kıyı şeridi
ayrıştırıcıları) Android'e bağlı olmadığı için JVM'de derlenip
çalıştırılabiliyor; sayısal çıktılar orijinal JavaScript sürümüne karşı
karşılaştırılıyor.

## Bilinen sınırlar

- Bu depo Android SDK'sız bir ortamda hazırlandı; APK derlemesi ilk kez
  sizin makinenizde çalışacak. Saf mantık katmanı (geometri, rota, durum,
  biçimlendirme) JVM'de derlenip orijinal JavaScript çıktısıyla karşılaştırıldı,
  Compose/servis katmanı ise derlenmedi.
- Servis `specialUse` foreground service tipi kullanır; Play Store'a yüklenecekse
  bu tip için gerekçe girmek gerekir.
