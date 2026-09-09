/**
 * Kabin çanı. WebAudio ile üç kısa motif — dosya yok, gecikme yok.
 *
 * Mobilde bu katman değişecek: iOS'ta sessiz anahtarı açıkken WebAudio hiç ses
 * çıkarmaz, o yüzden faz bitişinin duyulması bildirimin kendi sesine ve
 * titreşime bağlanacak. Çan yalnızca uygulama öndeyken çalar.
 */

const SEQUENCES = {
  level:    [[880, 0], [659, 0.26]],              // seyir yüksekliğine ulaşıldı
  land:     [[659, 0], [523, 0.22], [392, 0.44]], // iniş
  boarding: [[659, 0], [880, 0.24]]               // molanın sonu, yeniden biniş
};

let ctx = null;

export function chime(kind) {
  const seq = SEQUENCES[kind] ?? SEQUENCES.level;
  try {
    ctx = ctx || new (window.AudioContext || window.webkitAudioContext)();
    if (ctx.state === "suspended") ctx.resume();

    for (const [freq, at] of seq) {
      const t0 = ctx.currentTime + at;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = "triangle";
      osc.frequency.value = freq;
      gain.gain.setValueAtTime(0, t0);
      gain.gain.linearRampToValueAtTime(0.16, t0 + 0.012);
      gain.gain.exponentialRampToValueAtTime(0.0008, t0 + 0.9);
      osc.connect(gain).connect(ctx.destination);
      osc.start(t0);
      osc.stop(t0 + 1);
    }
  } catch {
    /* ses açılamadı — sessiz devam et */
  }
}
