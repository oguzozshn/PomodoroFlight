# Uçuş Modu — Android

> Bu dosya ayrıntılı geliştirici notlarıdır. Projenin genel tanıtımı ve
> İngilizce girişi için depo kökündeki [`README.md`](../README.md) dosyasına
> bakın.

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

SDK olmadan da saf katman derlenip sınanabiliyor; bkz. [Testler](#testler).

## Web sürümünden ne taşındı

| Web | Android |
|---|---|
| Canvas'ta ortografik küre, paralel/meridyen ağı, büyük daire rotası | `ui/Globe.kt` — Compose `Canvas`, aynı izdüşüm ve aynı geometri; kamera `ui/GlobeCamera.kt` |
| `localStorage` | `data/Store.kt` — SharedPreferences üzerinde JSON; kaydın kendisi `data/Persisted.kt` |
| `requestAnimationFrame` sayacı | `timer/FlightEngine.kt` — tek otorite, `StateFlow`; faz geçişi `timer/FlightCore.kt` |
| WebAudio kabin sesi | `audio/Chime.kt` — aynı notalar `AudioTrack` ile sentezleniyor |
| Sekme başlığındaki geri sayım | Foreground service bildirimi (Beklet; molada Molayı atla) |
| Barlow · Barlow Condensed · IBM Plex Mono (CDN) | Aynı fontlar `res/font` içinde gömülü |
| — | `ui/window/` — uçuş sırasında tam ekran pencere görünümü (web sürümünde yok) |

Havalimanı tablosu, ön tanımlı rotalar, `TKxxxx` uçuş numarası üretimi ve
4 pomodoroda bir uzun mola döngüsü web sürümüyle birebir aynıdır; sayısal
çıktılar orijinal JavaScript'e karşı doğrulanmıştır.

**Bir pomodoro 25 dakika ve uçağın gerçekten aldığı yol kadar.** Web
sürümünde her pomodoro sabit 850 km taşıyordu; bu, uçağın bir saatte aldığı
yol. Artık ölçek gerçek seyir hızına bağlı (`data/Geo.kt`): 850 km/sa ×
25 dk ≈ **354 km / pomodoro**, odakta geçen her dakika ≈ 14 km.

- Rotanın pomodoro (bacak) sayısı `km / 354` yuvarlanarak bulunur ve odak
  süresinden bağımsızdır: IST–JFK 23 pomodoro, bacak başına 349 km.
- 50 dakikalık bir oturum iki bacak, 15 dakikalık bir oturum 0,6 bacak yol
  alır; uçuş planındaki kutu kesirli dolar.
- Rota ekranındaki süre yer payı eklenmemiş **gerçek seyir süresi**; odak
  süresiyle arasındaki fark yalnızca molalardan gelir.
- İstatistiklerdeki pomodoro sayıları `dakika / 25`, yani kesirli.

## Uygulama akışı

Gerçek bir uçuş günü gibi dört adım:

1. **Terminal** (`ui/WelcomeScreen.kt`) — saat, küre, toplam istatistikler ve
   **Yolculuğa başla**. Süren bir uçuş varsa doğrudan devam edilebilir.
2. **Rota seç** (`ui/RouteScreen.kt`) — küreyi çevirip şehre dokunarak ya da
   arama kutusundan kalkış ve varış seçilir; hazır rotalar da duruyor. Biniş
   kartında uçuş no, kapı, kalkış ve tahmini varış saati, odakta geçecek süre
   ve gerçek seyir süresi yazar. **Odak, mola ve uzun mola** süreleri de
   burada, düğmenin hemen üstünde seçilir; uçuş sürerken kilitlidir.
   Düğme duruma göre **Bilet kes**, **Uçuşa dön** ya da **Tekrar uç** olur.
3. **Bilet** (`ui/TicketScreen.kt`) — "Bilet kes" denince biniş kartı yazıcı
   yuvasından duraklayarak beslenir, sonra **parmakla koparılır**: perforasyon
   üzerinde sağa kaydırdıkça yırtık ilerler, koparılan parça açıyla sarkar ve
   her tırtıkta kısa bir titreşim gelir. Yarıdan azken bırakılırsa geri yapışır.
   Uçuş ancak bilet koparılınca başlayabilir.
   Koçanda havayolu ve rota, koparılan parçada yolcu adı, uçuş numarası, kapı,
   koltuk, biniş/kalkış/varış saatleri, mesafe ve dekoratif barkod var. Koltuk
   rotaya ve güne sabitlenmiştir: aynı gün aynı rota aynı koltuğu verir.
4. **Uçuş** (`ui/FlightScreen.kt`) — küre, sayaç, uçuş planı ve ayarlar.
   Kalkış bilette olduğu için ayrı bir Kalkış düğmesi yok: sayaç yalnızca
   Beklet / Devam / Molayı başlat. **Odak oturumu atlanamaz**, mola
   atlanabilir. **Uçuşu bitir** kalan kısmı bırakır; inişte **Tekrar uç** aynı
   rotayı yeni biletle sıfırdan başlatır. Üstteki **Rota** düğmesi ikinci
   adıma döner. Yolcu adı buradaki ayarlardan giriliyor; boş bırakılırsa
   bilette "YOLCU" yazar.

Geri tuşu bu zinciri tersten yürür; sayaç hangi ekranda olursanız olun işlemeye
devam eder.

## Uçuş kaydı

Aynı anda **tek bir etkin uçuş** var (`data/Flights.kt`, `ActiveFlight`).
Eskiden ilerleme rota başına bir km sayacıydı: bir rotadan çıkıp dönünce uçak
kaldığı yerden devam ediyor, yarıda bırakılan uçuş hiçbir listede görünmüyordu.

- Uçuş **bilet koparılınca sıfırdan** başlar; aynı rotada önceki ilerleme
  devralınmaz.
- **Varışta** günlüğe tamamlanmış satır düşer (`✓ 23 pom · 8.026 km`), ekran
  yeni bilet kesilene kadar "İniş yapıldı" durumunda kalır.
- **Uçuşu bitir** ile yarıda bırakılan uçuş, yol alındıysa yarım satır olarak
  yazılır (`%30 · 7 / 23 pom · 2.444 km`); süren oturum sayılmaz. Hiç yol
  alınmadıysa iz bırakmaz.
- Uçuş sürerken başka rota seçilince onay sorulur (Vazgeç / Bitir ve geç).
- Kürede yarım uçuşun yalnızca uçulan kısmı çizilir.

Eski kayıttaki rota başına ilerleme ilk açılışta bir kez dönüştürülür: açık
rotanınki etkin uçuş, diğerleri yarım satır olur. Günlükte tamamlanmış satırı
olan rotanın ilerlemesi atlanır — çok eski bir sürümün dönüşümü o değeri
850 yazmıştı ve rotadan hem uzun hem kısa olabiliyordu; bu gerçek bir emülatör
kaydında görüldü ve teste bağlandı.

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
denkleminden, boylam da yatay bileşenin dönme açısından bulunuyor.

Bu matematik `ui/GlobeCamera.kt` içinde, Compose'a hiç dokunmayan bir dosyada
duruyor; `Globe.kt` yalnızca çiziyor. Ayrıldıktan sonra iddia teste bağlandı —
ekrandaki 289 nokta × 6 ölçek — ve iki eksik çıktı:

- **Sinüs denkleminin iki kökü var.** Yalnızca birincisi alınıyordu. Ufka
  yakın örneklerde (denenen 1.300 örnekte 1-2 tanesi) yanlış kök seçilip
  kamera, parmağın altındaki noktayı kürenin **arka yüzüne** atıyordu. Kayma
  sıfırdı, nokta kaymıyordu — tamamen öteki tarafa geçiyordu. Artık iki kök de
  deneniyor ve seçilen kamera geri izdüşürülerek doğrulanıyor.
- **Tam kutupta** `|pz| ≤ rr` eşitliğinin sağlanması gerekirken `rr`
  yuvarlama yüzünden 1'in bir ulp altında kalıyor, çözüm yok sanılıp merkeze
  göre yakınlaştırılıyordu. Sınıra pay eklendi.

Bu ikisinden sonra kayma her ölçekte ve her noktada 0,01 pikselin altında —
artık her koşuda ölçülüyor.

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

## Sayacın çekirdeği

Faz geçişi bir süre `FlightEngine` içinde, servis / bildirim / ses / titreşim /
dosya çağrılarıyla iç içe duruyordu. Uygulamanın en can alıcı mantığı bu ve
tam da bu yüzden hiç sınanamıyordu: hepsini çalıştırmak için cihaz gerekiyordu.

`timer/FlightCore.kt` artık geçişin **sonucunu** saf bir tarif olarak
döndürüyor — yeni kalıcı veri, sonraki faz ve süresi, günlüğe yazılacak kayıt,
anons türü, molanın kendiliğinden başlayıp başlamayacağı. `FlightEngine`
yalnızca bu tarifi uyguluyor. `Persisted` de `Store.kt`'den kendi dosyasına
alındı; artık cihazsız derleniyor.

Ayrıldıktan hemen sonra iki hata çıktı:

**Taşan bacak bedava mil veriyordu.** Kazanılan mesafe, rotada gerçekten
ilerlenen kadar değil, nominal bacak boyu kadar yazılıyordu. İkisi normalde
aynı (bacak boyu = km / bacak). Ama odak süresi rota yarım kalmışken
uzatılınca bacak boyu büyüyor ve son bacak rotanın ucundan taşıyor: IST-JFK'de
8 bacak uçup 25'ten 50 dakikaya geçen biri son pomodoroda 891 km ilerliyor ama
toplamına 1.605 km yazılıyordu — hiç uçmadığı 713 km. Günlük kaydına da aynı
şişirilmiş rakam düşüyordu. Artık ikisi de gerçekten kat edilen mesafeyi
yazıyor; süresini değiştirmeyen kullanıcıda davranış birebir aynı.

**Kayıt yanlış güne düşüyordu.** Günlük kaydı ile varış damgası "şu an" ile
tarihleniyordu. Oysa faz ekran kapalıyken ya da süreç ölmüşken bitmiş olabilir
ve saatler sonra fark edilir. Dokuz saat önce biten bir pomodoro bugüne
yazılıyor, gün dağılımını ve saat histogramını yanlış yerden dolduruyordu.
Artık fazın gerçekten bittiği an (`endAt`) taşınıyor; molayı atlamada bu an
zaten şimdiki zaman, yani orada bir değişiklik yok.

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

Bastırma ve kırpma `ui/window/SkyRings.kt` içinde, Compose'a dokunmayan bir
dosyada duruyor; `SkyGeometry.kt` yalnızca çiziyor. Ege hatası artık bir test:
ağzı ufka açılan bir körfez bastırılıyor ve çemberdeki ardışık noktalar
arasında yay adımından büyük bir sıçrama kalmadığı ölçülüyor — kiriş kalsaydı
o sıçrama radyanlar mertebesinde olurdu.

Test yazılırken yay adımının aslında tutturulmadığı da görüldü: bölme sayısı
aşağı yuvarlanıyordu, 2,7 radyanlık bir yay 54 yerine 53 parçaya bölünüp adım
istenen 0,05'in üstüne, 0,051'e çıkıyordu. Yukarı yuvarlanıyor artık.

### Video kipi ve telif

Video uygulamanın içine kopyalanmaz, APK'ya da girmez: SAF ile seçtiğiniz
dosyaya kalıcı okuma izni alınıp dosyanın kendisi okunur. Böylece depoda
hiçbir telifli görüntü durmuyor ve istediğiniz çekimi kullanabiliyorsunuz.

Göstergeler açıkken sayacı bekletebilir, molada molayı atlayabilirsiniz;
pencereden çıkmak gerekmiyor.

İyi bir pencere döngüsü için:

- **Dikey (9:16)**, 15–40 saniye, kamera sabit
- Başı ve sonu birbirine benzeyen bir çekim; ffmpeg ile 1–1.5 saniyelik
  çapraz geçiş konursa dönüş dikişi görünmez
- Sesi olmasın (uygulama zaten susturur), 720×1280 fazlasıyla yeter
- Günün farklı saatleri için ayrı dosyalar: gündüz, gün batımı, gece

## Uçuş günlüğü sekmesi

Alt gezinme çubuğundaki **Uçuş günlüğü** sekmesi (`ui/LogbookScreen.kt`);
terminal ekranındaki istatistik paneline dokununca da açılır. Kasıtlı olarak diğer pomodoro uygulamalarının
çubuk grafiğine benzemiyor: bu uygulamada dakikalar zaten mesafeye ve rotaya
çevrildiği için havayolu bildirimi daha doğru bir çerçeve.

- **Uçuş kartı** — toplam mil ve kart seviyesi (Classic / Silver / Elite /
  Elite Plus, eşikler dünya turuna bağlı), bir sonrakine kalan mesafe
- **Uçuşlar** — tamamlanan ve yarıda bitirilen bütün uçuşlar, en yenisi üstte
- **Dünya turu** — kaçıncı turda, turun neresinde
- **Uçuş haritası** — uçtuğunuz bütün rotaların izi küre üzerinde
- **Rakamlar** — seri, aktif gün, kayıtlı odak, tamamlanan uçuş, uğranan yer,
  en yoğun gün
- **Uçuş takvimi** — son 12 haftanın günlük yoğunluğu; bir güne dokununca
  o günün sayısı altta yazar
- **Saat dağılımı** — günün hangi saatlerinde çalıştığınız, en verimli aralık
  vurgulu
- **En çok uçulan hat**

### Günlük kayıt

Toplam sayaçlar (`totalKm`, `totalMs`) gün bilgisi taşımıyor, uçuş günlüğüne de
damga yalnızca bir rota *tamamen* bittiğinde düşüyordu. Gün bazlı her şey bu
yüzden yeni bir eklemeli kayda dayanıyor: `filesDir/ucus-gunlugu.tsv`,
tamamlanan her odak pomodorosu için bir satır.

```
# ucus-gunlugu v1: zaman	rota	dakika	km	atlandi
1757030400000	IST-JFK	25	349.0	0
```

`Store` içindeki JSON blob'una konmadı: o blob 12 ayrı yerden `persist()` ile
baştan yazılıyor, büyüyen bir listeyi oraya koymak her duraklatmada bütün
geçmişi yeniden yazmak olurdu. Room da alınmadı — günde 20 pomodoro yılda
~250 KB eden bir veri için yeni bir bağımlılık ve şema ağır kaçıyor. Depodaki
`UMC1`/`UMP2` ikili biçimleri taklit edilmedi çünkü onlar APK'ya gömülen salt
okunur varlıklar; bu ise kullanıcının kendi verisi, `adb shell cat` ile
okunabilmesi daha değerli. Dosya 4 MB'ı geçerse son 50.000 kayıt tutulur.

**Odak oturumu artık atlanamaz.** Eski sürümlerde ATLA ile geçilen
pomodorolar kayıtta işaretli durur (`atlandi` = 1); seriye, günlük toplama ve
saat dağılımına girmez.

**Geriye dönük veri gelmez.** Toplam mil ve uçuş günlüğü baştan beri gerçek,
duruyorlar; gün bazlı görünümler kayıt başladığı andan itibaren dolar. Mevcut
varış damgalarından sahte pomodoro kayıtları üretilebilirdi ama tarihleri
uydurma olurdu, yapılmadı — ekranın altında kaydın ne zaman başladığı yazıyor.

## Gezinme ve görünüm

Ekranlar arası geçiş gerçek bir yığın tutuyor (`ui/FlightRoot.kt`): geri tuşu
nereden gelindiğini biliyor. Önce sabit bir "ebeveyn" tablosu vardı ve
terminalden uçuşa devam edip geri basınca rota seçimine düşülüyordu.
Bilet koparılınca yığından düşürülür — geri tuşu yırtık bilete dönmez.

Kökte iki sekme var: **Uçuş** (bu yığın) ve **Uçuş günlüğü**. Telefonda alt
gezinme çubuğu, 720dp üstünde yan ray (`ui/FlightTabs.kt`). Bilet ve
pencereden bakış tam ekran kalsın diye orada çubuk gizlenir. Sekme değişince
uçuş yığını olduğu gibi kalır; geri tuşu önce günlükten uçuşa döner.

Çıkış her ekranda aynı yerde: **sol üstte "← Geri"**. Geri alınamayan tek
eylem (her şeyi sil) gezinme düğmelerinden ayrı, kendi satırında ve tehlike
renginde; iki dokunuşla onaylanır.

Görünüm **Sistem / Açık / Koyu / Pembe** olarak ayarlardan seçilir ve cihazda
saklanır. Pembe, açık temanın pembe tonlu hali (vurgu rengi amber yerine gül);
masaüstündeki pembe temayla aynı değerler. Durum ve gezinme çubukları da
seçilen temanın zemin rengini alır.
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
Bilet ve uçuş günlüğü ortalanmış tek sütun olarak kalır.

Yön kilidi yok; düzen genişliğe göre kendini ayarlar.

**Titreşim:** bu sınıftaki tabletlerin çoğunda motor yok. Kod `hasVibrator()`
ile korunuyor, çökmez — ama bilet koparma jestinin dokunsal geri bildirimi
o cihazlarda olmaz.

## Testler

```bash
cd android
./gradlew test          # 141 birim test, JVM'de çalışır, cihaz gerekmez
```

Android SDK yoksa Gradle daha ilk adımda durur. Testler yine de koşar:

```bash
python3 tools/run_tests.py            # aynı 141 test, gerçek derleyiciyle
python3 tools/run_tests.py --offline  # ağa hiç çıkma
```

Betik Kotlin 2.0.21 derleyicisini ve JUnit'i Maven Central'dan indirir,
SHA-1 ile doğrular, `.kotlin-toolchain/` altında saklar ve saf katmanı
gerçekten derler. Bu depoda uzun süre yalnızca derleyicisiz tarama vardı;
artık testlerin geçtiği iddiası burada da doğrulanabiliyor.

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

Sayacın faz geçişi (`timer/FlightCore.kt`) ve kürenin kamerası
(`ui/GlobeCamera.kt`) sonradan Android katmanından ayrıldı; ikisi de artık
buradan sınanıyor; kıyıları ufka oturtan geometri de öyle
(`ui/window/SkyRings.kt`). Ayırmanın kendisi beş hata ortaya çıkardı: ikisi
[Küre](#küre), ikisi [Sayacın çekirdeği](#sayacın-çekirdeği), biri
[Pencereden bakış](#pencereden-bakış) bölümünde anlatılıyor.

## Derlemeden önce

Compose katmanı burada derlenemiyor: androidx yapıtlarının tek dağıtım adresi
Google'ın kendi Maven deposu. `tools/lint_kotlin.py` derleyici olmadan
yakalanabilen hata sınıflarını tarar: dengesiz ayraçlar, **dengesi tutan ama
yuvalanması kaymış dosyalar** (kapanış parantezi yanlış fonksiyonun sonuna
konunca aradaki bütün fonksiyonlar bir üstekinin içine gömülür; toplam sayı
tuttuğu için ayraç sayımı yakalamaz), tanımsız tür/fonksiyon adları ve JVM
imza çakışmaları (`var zoom` ile `fun setZoom` aynı imzayı üretir).

```bash
python3 tools/lint_kotlin.py android/app/src/main/java
```

Taramanın kendi körlüğü de bu turda çıktı: uzantı fonksiyonunun alıcısı
(`fun Projection.pathOf`) hiç bakılmayan bir yerdi. `Projection` adı
`GlobeCamera` olunca üç yerde eski ad kaldı ve tarama "temiz" dedi. Alıcı
türleri ile `is Foo` / `as Foo` artık taranıyor.

Saf mantık katmanı Android'e bağlı olmadığı için gerçek Kotlin derleyicisiyle
derlenip çalıştırılabiliyor (`tools/run_tests.py`); sayısal çıktılar orijinal
JavaScript sürümüne karşı karşılaştırılıyor.

## Yedekleme

`allowBackup="true"` idi ama hiçbir kural yoktu; neyin yedekleneceğine Android
kendi karar veriyordu. Artık açıkça yazıyor (`res/xml/backup_rules.xml` ve
Android 12+ için `res/xml/data_extraction_rules.xml`; ikisi birden gerekiyor,
eski sürümler yalnızca öbürünü okuyor).

Yedeklenen şey kullanıcının kazandığı şey: sayacın kalıcı kaydı
(`ucus-modu.xml`) ve gün bazlı pomodoro dosyası (`ucus-gunlugu.tsv`).

Pencere tercihleri (`ucus-modu-pencere.xml`) bilerek dışarıda: içindeki video
adresi SAF ile alınmış bir izne dayanıyor ve o izin başka cihaza taşınmıyor,
yani geri yüklenen adres kesinlikle ölü olurdu. Kalanları (tema, kanat,
çerçeve) tek dokunuşla yeniden seçmek daha ucuz.

## Bilinen sınırlar

- Saf mantık katmanı burada gerçek derleyiciyle derlenip sınanıyor
  (`tools/run_tests.py`), Compose ve servis katmanı ise derlenmiyor: androidx
  yapıtları yalnızca Google'ın kendi Maven deposundan dağıtılıyor. O katmanların
  ilk derlemesi sizin makinenizde çalışacak.
- Servis `specialUse` foreground service tipi kullanır; Play Store'a yüklenecekse
  bu tip için gerekçe girmek gerekir.
- `USE_EXACT_ALARM` iznini bildiriyor; Play bunu yalnızca çalar saat ve takvim
  uygulamalarına veriyor. Yandan yüklemede sorun yok ve izin olmadığında kod
  zaten `SCHEDULE_EXACT_ALARM`'a, o da yoksa uyku dostu alarma düşüyor —
  Play'e çıkılacaksa bu iznin kaldırılması gerekir.
