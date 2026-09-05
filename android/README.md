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
| — | `ui/window/` — uçuş sırasında tam ekran pencere görünümü (web sürümünde yok) |

Havalimanı tablosu, ön tanımlı rotalar, `TKxxxx` uçuş numarası üretimi ve
4 pomodoroda bir uzun mola döngüsü web sürümüyle birebir aynıdır; sayısal
çıktılar orijinal JavaScript'e karşı doğrulanmıştır.

**Ölçek pomodoro sayısına değil süreye bağlı.** Web sürümünde her pomodoro
sabit 850 km taşıyordu, yani odak süresini uzatmak mesafeyi değiştirmiyordu.
Burada ölçek dakikaya bağlı: odakta geçen her dakika 34 km. Varsayılan
25 dakikada rakamlar birebir aynı (25 × 34 = 850), ama 50 dakikalık bir
pomodoro artık iki kat yol alıyor ve bir rotayı bitirmek için gereken toplam
odak süresi pomodoro uzunluğundan bağımsız hale geliyor. İlerleme de bacak
sayısı olarak değil kat edilen km olarak saklanıyor; böylece odak süresini
değiştirmek yarım kalmış bir rotayı bozmuyor. Eski kayıtlar (`state-v1`) ilk
açılışta rotanın kendi bacak uzunluğuyla km'ye çevriliyor.

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

## Pencereden bakış

Uçuş ekranındaki **Pencereden bak** düğmesi tam ekran bir pencere görünümü
açar (`ui/window/`). Sistem çubukları gizlenir, ekran uyanık kalır, ekrana
dokununca göstergeler çekilir; geriye yalnızca pencere ve kalan süre kalır.
Üç kaynak arasında gidip gelinebilir ve seçim cihazda saklanır:

| Kip | Ne gösterir |
|---|---|
| **Gerçek zamanlı** | Rotanın o anki noktasından, gerçek saate göre aydınlanan bir gökyüzü |
| **Küre** | Giriş ekranındaki kürenin uçağı izleyen hali |
| **Video** | Kendi seçtiğiniz pencere görüntüsü, sessiz ve döngüde |

### Gerçek zamanlı kip nasıl çiziliyor

`ui/window/SkyCamera.kt` iğne delikli bir kamera: bütün hesaplar dünya merkezli
birim vektörlerle yapılır, bir noktanın ekrandaki yeri üç iç çarpım ve tek bir
bölmedir. Ufkun kavisi, güneş yüksekliğine göre değişen renk takımı
(`SkyPalette.kt`), altta geçen kara parçaları, paralakslı bulut tabakası,
geceleyin yıldızlar ve şehir ışıkları hepsi aynı kameradan geçer. Kanat ve
kabin çerçevesi uçağa bağlı olduğu için dünya çalkanırken sabit durur.

Kara parçalarının çizimi (`SkyGeometry.kt`) küçük ama önemli bir ayrıntıya
dayanıyor: ufkun ötesindeki noktalar atılmaz, bulundukları yönde ufuk
çemberine **bastırılır**. Önce kırpma denendi; kırpılan halkanın iki ucunu
birleştiren kiriş Ege'yi baştan başa kara yapıyordu, çünkü körfezin ağzı ufka
açılıyor. Bastırma halkayı kapalı ve aynı yönde dönen tek bir eğri olarak
bıraktığı için sorun kökten kalkıyor. Aynı geçişte arkada kalan kıyılar
seyreltiliyor — bir kıtanın on bin noktası birkaç yüze iniyor — sonra halka
görüş piramidinin beş düzlemine kırpılıyor.

### Video kipi ve telif

Video uygulamanın içine kopyalanmaz, APK'ya da girmez: SAF ile seçtiğiniz
dosyaya kalıcı okuma izni alınıp dosyanın kendisi okunur. Böylece depoda
hiçbir telifli görüntü durmuyor ve istediğiniz çekimi kullanabiliyorsunuz.

Göstergeler açıkken sayacı bekletip atlayabilirsiniz; pencereden çıkmak
gerekmiyor.

İyi bir pencere döngüsü için:

- **Dikey (9:16)**, 15–40 saniye, kamera sabit
- Başı ve sonu birbirine benzeyen bir çekim; ffmpeg ile 1–1.5 saniyelik
  çapraz geçiş konursa dönüş dikişi görünmez
- Sesi olmasın (uygulama zaten susturur), 720×1280 fazlasıyla yeter
- Günün farklı saatleri için ayrı dosyalar: gündüz, gün batımı, gece

## Mil bildirimi

Terminal ekranındaki istatistik paneline (ya da uçuş günlüğündeki **Tümü**
düğmesine) dokununca açılır. Kasıtlı olarak diğer pomodoro uygulamalarının
çubuk grafiğine benzemiyor: bu uygulamada dakikalar zaten mesafeye ve rotaya
çevrildiği için havayolu bildirimi daha doğru bir çerçeve.

- **Uçuş kartı** — toplam mil ve kart seviyesi (Classic / Silver / Elite /
  Elite Plus, eşikler dünya turuna bağlı), bir sonrakine kalan mesafe
- **Dünya turu** — kaçıncı turda, turun neresinde
- **Uçuş haritası** — uçtuğunuz bütün rotaların izi küre üzerinde
- **Rakamlar** — seri, aktif gün, kayıtlı odak, tamamlanan uçuş, uğranan yer,
  en yoğun gün
- **Uçuş takvimi** — son 12 haftanın günlük yoğunluğu; bir güne dokununca
  o günün sayısı altta yazar
- **Saat dağılımı** — günün hangi saatlerinde çalıştığınız, en verimli aralık
  vurgulu
- **En çok uçulan hat** ve **uçuş günlüğü**

### Günlük kayıt

Toplam sayaçlar (`totalKm`, `totalMs`) gün bilgisi taşımıyor, uçuş günlüğüne de
damga yalnızca bir rota *tamamen* bittiğinde düşüyordu. Gün bazlı her şey bu
yüzden yeni bir eklemeli kayda dayanıyor: `filesDir/ucus-gunlugu.tsv`,
tamamlanan her odak pomodorosu için bir satır.

```
# ucus-gunlugu v1: zaman	rota	dakika	km	atlandi
1757030400000	IST-JFK	25	850.0	0
```

`Store` içindeki JSON blob'una konmadı: o blob 12 ayrı yerden `persist()` ile
baştan yazılıyor, büyüyen bir listeyi oraya koymak her duraklatmada bütün
geçmişi yeniden yazmak olurdu. Room da alınmadı — günde 20 pomodoro yılda
~250 KB eden bir veri için yeni bir bağımlılık ve şema ağır kaçıyor. Depodaki
`UMC1`/`UMP2` ikili biçimleri taklit edilmedi çünkü onlar APK'ya gömülen salt
okunur varlıklar; bu ise kullanıcının kendi verisi, `adb shell cat` ile
okunabilmesi daha değerli. Dosya 4 MB'ı geçerse son 50.000 kayıt tutulur.

**ATLA ile geçilen pomodoro** kayda düşer ama işaretlenir: seriye, günlük
toplama ve saat dağılımına girmez. Mesafe ve rota ilerlemesi eskisi gibi
işler — orada bir davranış değişikliği yok.

**Geriye dönük veri gelmez.** Toplam mil ve uçuş günlüğü baştan beri gerçek,
duruyorlar; gün bazlı görünümler kayıt başladığı andan itibaren dolar. Mevcut
varış damgalarından sahte pomodoro kayıtları üretilebilirdi ama tarihleri
uydurma olurdu, yapılmadı — ekranın altında kaydın ne zaman başladığı yazıyor.

## Gezinme ve görünüm

Ekranlar arası geçiş gerçek bir yığın tutuyor (`ui/FlightRoot.kt`): geri tuşu
nereden gelindiğini biliyor. Önce sabit bir "ebeveyn" tablosu vardı ve
terminalden uçuşa devam edip geri basınca rota seçimine düşülüyordu.
Bilet koparılınca yığından düşürülür — geri tuşu yırtık bilete dönmez.

Çıkış her ekranda aynı yerde: **sol üstte "← Geri"**. Geri alınamayan tek
eylem (her şeyi sil) gezinme düğmelerinden ayrı, kendi satırında ve tehlike
renginde; iki dokunuşla onaylanır.

Görünüm **Sistem / Açık / Koyu** olarak ayarlardan seçilir ve cihazda saklanır.
Karanlık palet baştan beri vardı ama yalnızca sistem ayarını izliyordu.

Türkçe büyük harf için `String.upperTr()` kullanılır: Kotlin'in `uppercase()`
fonksiyonu yerelden bağımsız çalışıp `i` harfini `I` yapıyor, ekranda
"BILET KES" ve "HER ŞEYI SIL" yazıyordu.

## Tablet ve geniş ekranlar

Uygulama telefon için tasarlanmıştı ve her şey `fillMaxWidth()` idi; 9" bir
tablette satırlar ekranı baştan başa geçiyor, küre devleşiyor, geri sayıma
ulaşmak için kaydırmak gerekiyordu. `ui/Adaptive.kt` iki eşik tanımlar:

- **`ContentMaxWidth` (560dp)** — metin sütunu bundan geniş olmaz, ortalanır.
  Telefon zaten dar olduğu için hiç etkilenmez.
- **`TwoPaneMinWidth` (720dp)** — bu genişlikten sonra terminal ve uçuş
  ekranı iki panele ayrılır: solda küre ekran boyunca, sağda kaydırılabilir
  kumanda. Tablette yatayda hiçbir şeyi kaydırmadan hem dünyayı hem geri
  sayımı görürsünüz.

Rota ekranında küre bir haritadır, metinden geniş olabilir: `GlobeMaxWidth`
(900dp) kadar büyür ve geniş ekranda yüksekliği 300dp yerine 440dp olur.
Bilet ve mil bildirimi ortalanmış tek sütun olarak kalır.

Yön kilidi yok; düzen genişliğe göre kendini ayarlar.

**Titreşim:** bu sınıftaki tabletlerin çoğunda motor yok. Kod `hasVibrator()`
ile korunuyor, çökmez — ama bilet koparma jestinin dokunsal geri bildirimi
o cihazlarda olmaz.

## Testler

```bash
cd android
./gradlew test          # 79 birim test, JVM'de çalışır, cihaz gerekmez
```

`app/src/test/` altında saf mantık katmanının tamamı sınanıyor: küre
geometrisi (web sürümünün ürettiği sayılara karşı), rota kurulumu ve uçuş
numarası üretimi, uçuş süresi ve mola takvimi hesapları, Türkçe biçimlendirme,
güneş konumu (gündönümü ve ekinokslara karşı), durum türevleri (ilerleme oranı,
irtifa, kalan bacak), ikili varlık biçimleri ve Türkçe duyarlı arama
(Gaziemir → İzmir, Munich → Münih) ve bilet üretimi (koltuk kararlılığı,
saat sıralaması, barkod deseni). Pencere kamerası da buradan sınanıyor:
ufuk düşme açısı, odak uzaklığı, izdüşüm ve tersi, eğimli ufkun yüksekliği —
hepsi kapalı formüllere karşı ölçülüyor. Günlük kaydı da öyle: satır biçiminin
gidiş-dönüşü, bozuk satırların elenmesi, ondalık ayracın makineden bağımsız
olması, seri hesabı (boşluk, gün sınırı, yerel saat dilimi), günlük kutular,
saat histogramı ve kart seviyesi eşikleri.

## Derlemeden önce

Depoda Android SDK olmadığı için Compose katmanı burada derlenemiyor.
`tools/lint_kotlin.py` derleyici olmadan yakalanabilen dört hata sınıfını tarar:
dengesiz ayraçlar, **dengesi tutan ama yuvalanması kaymış dosyalar** (kapanış
parantezi yanlış fonksiyonun sonuna konunca aradaki bütün fonksiyonlar bir
üstekinin içine gömülür; toplam sayı tuttuğu için ayraç sayımı yakalamaz),
tanımsız tür/fonksiyon adları ve JVM imza çakışmaları (`var zoom` ile
`fun setZoom` aynı imzayı üretir).

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
