/**
 * Pencere sahnesinin çizim kalitesi.
 *
 * Masaüstünde telefondaki bütçe kısıtı yok, o yüzden ayarlanabilir bırakıldı.
 * `scale` tuvalin arka belleğini ekran çözünürlüğünün katına çıkarır — sahne
 * daha büyük çizilip küçültülerek gösterilir, kenarlar yumuşar (süper örnekleme).
 * Öteki alanlar sahnedeki nesne sayısını belirler.
 */

/**
 * Kalite sahnenin *içeriğini* değiştirmez, yalnızca ne kadar ince çizildiğini
 * belirler: bulut ve yıldız sayıları sabit kalır. Sayıları da artırsaydık
 * "yüksek kalite" havayı değiştirmiş olurdu — aynı anda aynı yerde daha çok
 * bulut çıkardı.
 *
 * `scale` tuvalin arka belleğini ekran çözünürlüğünün katına çıkarır: sahne
 * büyük çizilip küçültülerek gösterilir, kenarlar yumuşar (süper örnekleme).
 * `horizonSteps` ufuk eğrisinin kaç parçadan kurulduğu.
 */
export const CLOUD_COUNT = 190;
export const STAR_COUNT = 260;

export const QUALITY = {
  normal: { title: "Normal", scale: 1, horizonSteps: 96 },
  high: { title: "Yüksek", scale: 1.5, horizonSteps: 160 },
  ultra: { title: "Çok yüksek", scale: 2, horizonSteps: 240 }
};

export const DEFAULT_QUALITY = "normal";

export const qualityOf = key => QUALITY[key] ?? QUALITY[DEFAULT_QUALITY];

/**
 * Koltuk tarafı: pencere burnun bu kadar yanına bakar.
 * Sol koltukta sola, sağ koltukta sağa; kanat da aynalanır.
 */
export const VIEW_OFFSET_DEG = 78;

export const offsetForSeat = seat => (seat === "right" ? VIEW_OFFSET_DEG : -VIEW_OFFSET_DEG);
