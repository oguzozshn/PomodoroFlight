# Uçuş Modu — masaüstü

Rota tabanlı pomodoro sayacı. Bir odak seansı ≈ 850 km seyir; bir rotayı
bitirmek için gereken pomodoro sayısı iki havalimanı arasındaki büyük daire
mesafesinden hesaplanır.

`android/` altındaki native sürümün Windows karşılığı; Electron ile
paketlenir. Veri katmanı (ikili harita biçimleri, rota hesabı, günlük, bilet,
gökyüzü kamerası) Kotlin'den birebir taşındı. Uygulama tamamen çevrimdışı
çalışır — yazı tipleri de dahil hiçbir şey ağdan çekilmez.

## Çalıştırma

```bash
npm install
npm start
```

`npm install` sonrası Electron ikilisi inmediyse (npm betikleri kapalıysa):

```bash
node node_modules/electron/install.js
```

## Rota seçme

Üç yol var:

- **Küreden**: kalkış havalimanına tıkla, sonra varışa. İki nokta arasındaki
  büyük daire imleci takip ederek önizlenir. Küreyi sürükleyerek çevir, fare
  tekerleğiyle yakınlaştır (1×–4×), boş bir yere çift tıklayarak yakınlaştırmayı
  sıfırlayıp rotaya ortala, `Esc` ile seçimden vazgeç.
- **Hazır rotalar**: sağdaki listeden.
- **Açılır menüler**: "Kendi rotanı kur" bölümünden.

## Paketleme

```bash
npm run dist
```

`release/UcusModu-Kurulum-1.0.0.exe` üretilir: ~107 MB, kendi kendine yeten bir
Windows kurulum sihirbazı. Hedef makinede Node, npm ya da başka bir şey gerekmez —
Chromium çalışma zamanı, uygulama kodu, fontlar ve veri dosyaları (3.941
havalimanı, Natural Earth kara parçaları) paketin içinde.

Kurulum kullanıcı bazında (`perMachine: false`), yönetici hakkı istemez; kurulum
yeri değiştirilebilir, Başlat menüsüne "Uçuş Modu" kısayolu düşer ve Ayarlar →
Uygulamalar'dan kaldırılabilir.

**Paket imzasız.** İlk çalıştırmada Windows SmartScreen "bilinmeyen yayımcı"
uyarısı gösterir; **Daha fazla bilgi → Yine de çalıştır** ile geçilir. İmzalamak
için ücretli bir kod imzalama sertifikası gerekir.

Yalnızca klasör çıktısı (kurulum paketi olmadan) için `npm run pack`.

## Yapı

Kod üç katmana ayrılmış: `core/` hiçbir platform bilgisi taşımaz, `platform/`
işletim sistemine açılan tek yüzeydir, `ui/` ikisini ekrana bağlar.

```
src/core/airports.js        elle yazılmış Türkçe adlar ve hazır rotalar
src/core/places.js          3.941 havalimanı (places.bin), Türkçe duyarlı arama
src/core/turkish-cities.js  veri setindeki adların Türkçe karşılıkları
src/core/shapes.js          Natural Earth kara ve göl katmanları (land_*.bin)
src/core/geo.js             haversine, slerp, ortografik izdüşüm, ölçek sabitleri
src/core/sun.js             alt-güneş noktası ve güneş yüksekliği
src/core/route.js           rota modeli: mesafe, bacak sayısı, uçuş numarası
src/core/state.js           türetilmiş uçuş durumu (oran, irtifa, kalan bacak)
src/core/timer.js           faz makinesi, mutlak bitiş zamanı üzerinden
src/core/store.js           kalıcı durum, v1'den v2'ye göç
src/core/journal.js         tamamlanan pomodoroların eklemeli günlüğü
src/core/journal-stats.js   seri, ısı takvimi, saat histogramı, en çok uçulan hat
src/core/card-tier.js       kart seviyeleri (Classic → Elite Plus)
src/core/ticket.js          biniş kartı: koltuk, kapı, saatler, barkod
src/core/estimates.js       uçuş süresi, takvim süresi, dünya turu
src/core/format.js          Türkçe biçimlendirme, upperTr

src/platform/index.js       köprü: window.desktop (Electron) / window.native
src/platform/assets.js      gömülü ikili varlıkları okur
src/platform/chime.js       kabin çanı (WebAudio)
src/platform/journal-storage.js   günlük dosyası ya da localStorage

src/ui/globe.js             küre: kara, gündüz/gece, 0,55×–18× yakınlaştırma
src/ui/route-picker.js      kalkış/varış yuvaları, arama, biniş kartı önizlemesi
src/ui/ticket.js            biletin yazdırılması ve koparılması
src/ui/logbook.js           mil bildirimi
src/ui/window/              pencereden bakış: kamera, renk takımı, geometri, sahne
src/ui/app.js               DOM bağlama, çizim döngüsü, açılış

electron/main.js            pencere, menü, bildirim, görev çubuğu, tema
electron/preload.js         sayfaya açılan tek köprü (window.desktop)
electron/serve.js           src/ klasörünü app:// şeması altından servis eder
electron/journal.js         uçuş günlüğü dosyası (userData altında)
electron/media.js           pencere görüntüsü olarak video seçme
```

Sayfa `file://` yerine `app://ucus/index.html` adresinden yükleniyor: ES
modülleri `file://` üzerinden yüklenmez.

## Ölçek

Odakta geçen her dakika 34 km taşır — ölçek pomodoro sayısına değil süreye
bağlı. 50 dakikalık bir pomodoro 25 dakikalığın iki katı yol alır, bir rotayı
bitirmek için gereken toplam odak süresi pomodoro uzunluğundan bağımsız kalır.
İlerleme bacak sayısı olarak değil kat edilen km olarak saklanır; böylece odak
süresini değiştirmek yarım kalmış bir rotayı bozmaz. Eski kayıt (`ucus-modu-v1`)
ilk açılışta rotanın kendi bacak uzunluğuyla km'ye çevrilir.

## Küre

Kara parçaları Natural Earth'ün kamu malı verisinden geliyor; dosyalar Android
sürümünün ürettiği ikili biçimde (`UMC1`) olduğu gibi okunuyor. Kaba katman
(21 KB) her zaman, ince katman (243 KB) yakınlaşınca yükleniyor.

Ufkun ötesine düşen noktalar atılmaz, bulundukları yönde ufka **bastırılır**;
kırpma denendiğinde halkanın iki ucunu birleştiren kiriş, ağzı ufka açılan
körfezleri baştan başa kara yapıyordu.

- **Döndürme:** sürükleme; bırakınca sönümlenerek dönmeye devam eder
- **Yakınlaştırma:** fare tekerleği, 0,55×–18×. İmlecin altındaki coğrafi nokta
  yerinde tutulur: yaklaşım yapılmaz, kamera doğrudan çözülür.
- **Çift tıklama:** yakınlaştırmayı sıfırlar ve rotaya ortalar
- **Gündüz/gece:** karanlık yarı gölgelenir; alt-güneş noktası hesaplanır
- **İzler:** tamamlanan uçuşlar kürede soluk çizgiler olarak kalır

## Rota seçme

- **Küreden:** kalkışa tıkla, sonra varışa; arada büyük daire önizlenir
- **Arama:** şehir adı, havalimanı adı ya da IATA kodu. Türkçe büyük/küçük harf
  ve aksan farkları normalleştirilir (`münih` → MUC, `diyarbakir` → DIY). Aynı
  şehirde birden çok havalimanı varsa havalimanı adı da yazılır.
- **Hazır rotalar:** sağdaki listeden

## Biniş kartı

Kalkış'a basınca bilet yazıcı yuvasından duraklayarak beslenir, sonra
perforasyon boyunca sürüklenerek koparılır: yarıyı geçmeden bırakılırsa geri
yapışır. Uçuş ancak bilet koparılınca başlar. Koltuk rotaya ve güne
sabitlenmiştir — aynı gün aynı rota aynı koltuğu verir.

## Menüler

**Ayarlar** ve **İstatistikler** menü çubuğunda kendi girişleri; `Ctrl+,` ve
`Ctrl+I` ile de açılırlar. Ayarlar üç grupta toplanır: sayaç (odak/mola süresi,
molaların kendiliğinden başlaması), pencereden bakış (koltuk tarafı, görüntü
kalitesi, kanat modeli) ve biniş kartı (yolcu adı).

## Mil bildirimi

Uçuş günlüğündeki **Tümü** düğmesiyle açılır: uçuş kartı ve kart seviyesi,
dünya turu, uçtuğun rotaların küre üzerindeki izleri, seri ve aktif gün gibi
rakamlar, son 12 haftanın ısı takvimi, saat dağılımı, en çok uçulan hatlar.

Gün bazlı kayıt `ucus-gunlugu.tsv` dosyasında tutulur (Dosya → Verilerin yeri):
tamamlanan her odak için bir satır, sekmeyle ayrılmış düz metin. Kalıcı durumun
JSON'una konmadı — o kayıt her duraklatmada baştan yazılıyor. **Atla** ile
geçilen pomodoro kayda düşer ama işaretlenir; seriye, günlük toplama ve saat
dağılımına girmez.

## Pencereden bakış

Başlıktaki **Pencereden bak** tam ekran bir kabin penceresi açar. Bir süre
kıpırdanmayınca göstergeler çekilir, geriye pencere ve kalan süre kalır. Üç
kaynak arasında gidip gelinir, seçim saklanır:

| Kip | Ne gösterir |
|---|---|
| **Gerçek zamanlı** | Rotanın o anki noktasından, gerçek saate göre aydınlanan gökyüzü |
| **Küre** | Kürenin uçağı izleyen hâli |
| **Video** | Kendi seçtiğin pencere görüntüsü, sessiz ve döngüde |

Gerçek zamanlı kip iğne delikli bir kameradan çizilir: bir noktanın ekrandaki
yeri üç iç çarpım ve tek bölmedir. Ufkun kavisi, güneş yüksekliğine göre değişen
renk takımı, altta geçen kara parçaları, paralakslı bulut tabakası, geceleyin
yıldızlar ve şehir ışıkları hepsi aynı kameradan geçer.

Video uygulamanın içine kopyalanmaz: seçtiğin dosyanın yolu tutulur ve
`app://` altından okunur. Depoda hiçbir telifli görüntü durmaz.

## Masaüstüne özgü davranışlar

| Davranış | Nerede |
| --- | --- |
| Faz bitince işletim sistemi bildirimi | `notify` → `Notification` |
| Görev çubuğunda ilerleme çubuğu | `setProgress` → `win.setProgressBar` |
| Odak sürerken ekran uykuya dalmaz | `setKeepAwake` → `powerSaveBlocker` |
| Pencere konumu/boyutu hatırlanır | `window-state.json` |
| Sistem / açık / koyu / pembe tema menüden | `nativeTheme.themeSource`; pembe sistemde açık, sayfada `data-theme="pink"` (`onTheme`) |
| Menü kısayolları | `Ctrl+Space` başlat-beklet · `Ctrl+R` sıfırla · `Ctrl+→` atla · `Ctrl+M` ses |

Sayfa `window.desktop` köprüsü yokken de çalışır; bu çağrılar sessizce yok
sayıldığı için `src/index.html` düz bir tarayıcıda da açılabilir.

## Veriler

Uçuş günlüğü, ilerleme ve ayarlar `localStorage`'da; pencere durumu ve tema
tercihi `window-state.json` dosyasında tutulur. İkisi de Electron'un kullanıcı
veri klasöründedir — **Yardım → Veri klasörünü aç** ile ulaşılır.

## Güvenlik

`nodeIntegration` kapalı, `contextIsolation` ve `sandbox` açık; sayfa katı bir
CSP altında çalışır (`default-src 'none'`, ağ erişimi yok). Renderer'a yalnızca
`preload.js`'teki dört uç açıktır. Dış bağlantılar sistem tarayıcısında açılır.
