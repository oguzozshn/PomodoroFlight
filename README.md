# Uçuş Modu — Flight Mode

A pomodoro timer that flies you somewhere. Pick a departure and a destination,
and every minute you stay focused carries you 34 km along the great-circle
route between them. Finish the route and you land — the flight gets a stamp in
your logbook.

The interface is in Turkish. This README is the English entry point; the
detailed implementation notes live in [`android/README.md`](android/README.md)
(Turkish).

## What's in the repository

| Path | What it is |
|---|---|
| `ucus-modu.html` | The original — a single self-contained HTML file, no build step, no dependencies. Open it in a browser. |
| `android/` | A native Android app: Kotlin + Jetpack Compose, fully offline. |
| `desktop/` | A Windows desktop app: Electron, same features, fully offline. |
| `tools/` | Python scripts that build the offline map assets, plus a compiler-free Kotlin scanner. |

The Android app is not a WebView wrapper. Everything was reimplemented
natively, and the numeric behaviour is verified line-by-line against the
original JavaScript.

## The Android app

**A flight day in four steps.** Terminal → route selection → boarding pass →
flight. The boarding pass is fed out of a printer slot and then torn along the
perforation with your finger; let go before halfway and it sticks back.

**An offline globe.** Filled landmasses, a day/night terminator that follows
the real sun, drag-to-spin with momentum, anchored pinch-zoom, and roughly
6,000 searchable cities. The orthographic projection is derived from a camera
basis, so placing a point on screen costs three dot products and no
trigonometry per frame.

**A window seat.** During a flight you can go full screen and look outside,
with three sources you can switch between:

- **Real time** — a sky drawn from the aircraft's current position on the
  route and the actual time of day: horizon curvature, a colour ramp driven by
  solar elevation, landmasses passing underneath, a parallaxed cloud deck,
  and stars and city lights at night.
- **Globe** — the entry-screen globe, following the aircraft.
- **Video** — a window clip of your own, muted and looping. The file is never
  copied into the app or bundled into the APK; it is read in place through
  the Storage Access Framework, so no licensed footage ever enters the
  repository.

**A mileage statement.** Total distance and card tier, laps of the Earth, a
map of every route you have flown, a streak, a twelve-week activity calendar,
an hour-of-day distribution, and your most-flown routes.

**A timer that survives.** A foreground service plus exact alarms keep the
countdown honest with the screen off, and the state is reconstructed from the
wall clock if the process is killed. The cabin chime is synthesised with
`AudioTrack` rather than shipped as an audio file.

**Phone and tablet.** Below 720dp the layout is a single centred column; above
it, the terminal and flight screens split into two panes — globe on the left,
controls on the right.

## The Windows desktop app

The same app for a desktop, in Electron. Not a WebView of the HTML file
either: the data layer — the binary map formats, the great-circle routing,
the logbook, the boarding pass, the sky camera — was ported from the Kotlin
source, and the numbers match.

What the desktop gains over the phone: a two-column layout that shows the
globe and the flight controls at once, a menu bar (Uçuş · Ayarlar ·
İstatistikler · Görünüm · Yardım), keyboard shortcuts, a focus duration you
can type instead of picking from presets, a seat-side and wing-model choice
for the window view, and a render-quality setting that supersamples the sky.

```bash
cd desktop
npm install
npm start                   # run it
npm run dist                # release/UcusModu-Kurulum-<version>.exe (NSIS, unsigned)
```

The renderer runs sandboxed with context isolation and a strict CSP; the
files are served over a custom `app://` scheme rather than `file://`, because
ES modules do not load from an opaque origin.

## Building

The Android app requires Android Studio and an Android SDK. `minSdk 26`, `targetSdk 35`,
JDK 17, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.

```bash
cd android
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # install onto a connected device
./gradlew test              # 79 unit tests, JVM only, no device needed
```

## How this was verified

The app was written in an environment with no Android SDK, so the Compose
layer could not be compiled here. Three things stood in for a compiler:

1. **A JVM mirror of the pure layer.** Geometry, routing, state derivation,
   formatting, the binary asset parsers and the window camera have no Android
   dependencies, so they compile and run on a plain JVM. 79 unit tests cover
   them, several against closed-form formulas rather than recorded values.
2. **An oracle extracted from the original HTML.** Route distances, leg
   counts, flight numbers, interpolated positions, progress fractions and
   formatted output are compared against the original JavaScript. Parity has
   held through every refactor, including the switch to a minute-based
   distance scale.
3. **`tools/lint_kotlin.py`.** A compiler-free scanner for four error classes
   that are otherwise invisible without building: unbalanced brackets; files
   that balance but whose nesting has shifted (a closing brace placed at the
   end of the wrong function, which swallows every function in between);
   undefined type and function names; and JVM signature clashes.

Map geometry was also checked against ground truth — the drawn land fill is
queried point-by-point against the source polygons, so the sea reads as sea
and the coastlines land where they belong.

## Data and assets

- Coastlines and lakes: [Natural Earth](https://www.naturalearthdata.com/)
  (public domain), converted by `tools/build_shapes.py` into a compact binary
  format (int16 quantisation, ~340 m worst-case error).
- Places: [OurAirports](https://ourairports.com/data/) (public domain),
  filtered by `tools/build_places.py` to airports with scheduled service.
- Fonts: Barlow, Barlow Condensed and IBM Plex Mono (SIL Open Font License),
  embedded in `android/app/src/main/res/font/`.

No network access, no account, no analytics. Everything the app knows is on
the device.

## License

The project's own code is [MIT licensed](LICENSE).

The bundled fonts are not: Barlow, Barlow Condensed and IBM Plex Mono are
under the SIL Open Font License 1.1, which requires their notices to ship
alongside the font files. Those texts are in
[`third_party/fonts/`](third_party/fonts/). The map data (Natural Earth,
OurAirports) is public domain.

## Known limitations

- The Compose and service layers have never been compiled in this repository's
  development environment; the first build happens on your machine.
- The service uses the `specialUse` foreground service type, which requires a
  justification if the app is ever submitted to Google Play.
- The window view assumes a 9:16 frame. It scales on a tablet in landscape but
  the composition was tuned for portrait.
- Many tablets in this class have no vibration motor. The code guards for it,
  but the haptics of the ticket-tear gesture are lost on those devices.
