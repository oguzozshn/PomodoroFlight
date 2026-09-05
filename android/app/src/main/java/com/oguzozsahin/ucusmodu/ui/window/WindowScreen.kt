package com.oguzozsahin.ucusmodu.ui.window

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.Phase
import com.oguzozsahin.ucusmodu.data.Sun
import com.oguzozsahin.ucusmodu.data.WindowMode
import com.oguzozsahin.ucusmodu.data.WindowPrefs
import com.oguzozsahin.ucusmodu.data.WindowStore
import com.oguzozsahin.ucusmodu.data.formatClock
import com.oguzozsahin.ucusmodu.data.formatNumber
import com.oguzozsahin.ucusmodu.data.vecOf
import com.oguzozsahin.ucusmodu.ui.theme.LocalFlightColors
import com.oguzozsahin.ucusmodu.ui.theme.labelStyle
import com.oguzozsahin.ucusmodu.ui.theme.monoStyle
import kotlinx.coroutines.delay
import kotlin.math.asin

private val HudShape = RoundedCornerShape(4.dp)
private val HudBackground = Color(0xB3000000)
private val HudInk = Color(0xFFF2F5F9)
private val HudMuted = Color(0xFFA9B6C6)
private val HudAmber = Color(0xFFF2B457)

/** Dokunmadan gecince gostergeler kendiliginden silinir. */
private const val HUD_TIMEOUT_MS = 5000L

/**
 * Tam ekran pencere gorunumu.
 *
 * Uc kaynak arasinda gecis yapilir — gercek zamanli cizim, kure ve kullanicinin
 * kendi videosu — ustunde kalan sureyi gosteren ince bir gosterge durur.
 * Ekrana dokununca gostergeler gizlenir; boylece odakta yalnizca pencere kalir.
 */
@Composable
fun WindowScreen(
    state: FlightUiState,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalFlightColors.current
    val store = remember { WindowStore(context) }
    var prefs by remember { mutableStateOf(store.load()) }

    fun apply(next: WindowPrefs) {
        prefs = next
        store.save(next)
    }

    // Cihazdaki bir videoyu kalici okuma izniyle secmek icin.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            apply(prefs.copy(videoUri = uri.toString(), mode = WindowMode.VIDEO))
        }
    }

    // Tam ekran: sistem cubuklari gizlenir, ekran uyanik tutulur.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler { onExit() }

    // Kanat isiklari ve calkanti icin akan saniye.
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            seconds.floatValue = ((now - start) / 1e9).toFloat()
        }
    }

    // Gunes yuksekligi: kanadin tonu ve gece isiklari buna bagli.
    var sunElevation by remember { mutableStateOf(0.0) }
    val here = state.route.pointAt(state.fraction.toDouble())
    LaunchedEffect(here.lat, here.lon) {
        while (true) {
            val sun = Sun.subsolarPoint(System.currentTimeMillis())
            val dir = vecOf(sun.lat, sun.lon)
            val up = vecOf(here.lat, here.lon)
            val dot = (dir.x * up.x + dir.y * up.y + dir.z * up.z).coerceIn(-1.0, 1.0)
            sunElevation = Math.toDegrees(asin(dot))
            delay(30_000L)
        }
    }
    val palette = paletteFor(sunElevation)

    var hudVisible by remember { mutableStateOf(true) }
    LaunchedEffect(hudVisible, prefs.mode) {
        if (!hudVisible) return@LaunchedEffect
        delay(HUD_TIMEOUT_MS)
        if (prefs.mode != WindowMode.GLOBE) hudVisible = false
    }

    // Kure kendi dokunuslarini kullanir; oradaki gostergeler hep acik kalir.
    val tapToHide = if (prefs.mode == WindowMode.GLOBE) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            detectTapGestures { hudVisible = !hudVisible }
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black).then(tapToHide)) {
        when (prefs.mode) {
            WindowMode.REALTIME -> RealtimeWindow(
                route = state.route,
                fraction = state.fraction,
                altitudeM = state.altitudeM,
                modifier = Modifier.fillMaxSize(),
            )

            WindowMode.GLOBE -> GlobeWindow(
                route = state.route,
                fraction = state.fraction,
                colors = colors,
                modifier = Modifier.fillMaxSize(),
            )

            WindowMode.VIDEO -> {
                val uri = prefs.videoUri
                if (uri.isBlank()) {
                    VideoPrompt { picker.launch(arrayOf("video/*")) }
                } else {
                    VideoWindow(
                        uri = Uri.parse(uri),
                        modifier = Modifier.fillMaxSize(),
                        onFailed = { apply(prefs.copy(videoUri = "")) },
                    )
                }
            }
        }

        // Kanat ve cerceve ucaga bagli: dunya sallanirken bunlar sabit durur.
        if (prefs.mode != WindowMode.GLOBE) {
            WindowChrome(
                tint = wingTint(palette),
                // Videolarda ucagin kanadi zaten goruntude; ikinci kanat cizilmez.
                showWing = prefs.showWing && prefs.mode == WindowMode.REALTIME,
                showFrame = prefs.showFrame,
                time = seconds.floatValue,
                night = palette.night,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Hud(
                state = state,
                prefs = prefs,
                onMode = { apply(prefs.copy(mode = it)) },
                onWing = { apply(prefs.copy(showWing = it)) },
                onFrame = { apply(prefs.copy(showFrame = it)) },
                onPickVideo = { picker.launch(arrayOf("video/*")) },
                onExit = onExit,
            )
        }
    }
}

@Composable
private fun Hud(
    state: FlightUiState,
    prefs: WindowPrefs,
    onMode: (WindowMode) -> Unit,
    onWing: (Boolean) -> Unit,
    onFrame: (Boolean) -> Unit,
    onPickVideo: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .background(HudBackground, HudShape)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(state.route.flightNo, style = monoStyle(HudInk, 13, FontWeight.Medium))
                Text(
                    "${state.route.origin.code} → ${state.route.dest.code}",
                    style = monoStyle(HudMuted, 11),
                )
            }
            Spacer(Modifier.weight(1f))
            HudButton("KABİNE DÖN", onClick = onExit)
        }

        Spacer(Modifier.weight(1f))

        // Kalan sure ve ucusun ne kadarinin gectigi
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HudBackground, HudShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = when (state.phase) {
                    Phase.FOCUS -> "SEYİR"
                    Phase.BREAK -> "KISA MOLA"
                    Phase.LONG -> "UZUN MOLA"
                },
                style = labelStyle(HudAmber).copy(fontSize = 11.sp),
            )
            Text(
                text = formatClock(state.remainingMs),
                style = monoStyle(HudInk, 44, FontWeight.Light),
            )
            Spacer(Modifier.height(8.dp))
            ProgressRail(state.fraction)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${formatNumber(state.flownKm)} / ${formatNumber(state.route.km)} km" +
                    "   ·   ${formatNumber(state.altitudeM.toDouble())} m",
                style = monoStyle(HudMuted, 11),
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (mode in WindowMode.values()) {
                ModePill(
                    mode = mode,
                    active = mode == prefs.mode,
                    modifier = Modifier.weight(1f),
                    onClick = { onMode(mode) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (prefs.mode == WindowMode.REALTIME) {
                HudToggle("KANAT", prefs.showWing) { onWing(!prefs.showWing) }
            }
            if (prefs.mode != WindowMode.GLOBE) {
                HudToggle("ÇERÇEVE", prefs.showFrame) { onFrame(!prefs.showFrame) }
            }
            if (prefs.mode == WindowMode.VIDEO) {
                HudButton("VİDEO SEÇ", onClick = onPickVideo)
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = prefs.mode.hint,
            style = monoStyle(HudMuted, 10),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/** Ucusun ne kadarinin gectigini gosteren ince ray. */
@Composable
private fun ProgressRail(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color(0x33FFFFFF)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .background(HudAmber),
        )
    }
}

@Composable
private fun ModePill(
    mode: WindowMode,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = mode.title.uppercase(),
        style = labelStyle(if (active) HudAmber else HudMuted).copy(
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        ),
        modifier = modifier
            .background(HudBackground, HudShape)
            .border(1.dp, if (active) HudAmber else Color(0x33FFFFFF), HudShape)
            .clickable { onClick() }
            .padding(vertical = 9.dp),
    )
}

@Composable
private fun HudButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = labelStyle(HudInk).copy(fontSize = 11.sp),
        modifier = Modifier
            .background(HudBackground, HudShape)
            .border(1.dp, Color(0x33FFFFFF), HudShape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun HudToggle(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = if (active) "$text ✓" else text,
        style = labelStyle(if (active) HudAmber else HudMuted).copy(fontSize = 11.sp),
        modifier = Modifier
            .background(HudBackground, HudShape)
            .border(1.dp, if (active) HudAmber else Color(0x33FFFFFF), HudShape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Henuz video secilmemisken cikan aciklama. */
@Composable
private fun VideoPrompt(onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F16))
            .safeDrawingPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PENCERE VİDEOSU", style = labelStyle(HudAmber).copy(fontSize = 12.sp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Cihazınızdaki bir uçuş videosunu seçin; sessiz ve döngüde oynar. " +
                "Video uygulamanın içine kopyalanmaz, dosyanın kendisi okunur.",
            style = monoStyle(HudMuted, 12),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "En iyi sonuç: dikey (9:16), 15–40 saniye, kamera sabit, " +
                "başı ve sonu birbirine benzeyen bir çekim.",
            style = monoStyle(HudMuted, 11),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Box(modifier = Modifier.width(200.dp)) {
            Text(
                text = "VİDEO SEÇ",
                style = labelStyle(HudInk).copy(fontSize = 12.sp, textAlign = TextAlign.Center),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x22FFFFFF), HudShape)
                    .border(1.dp, HudAmber, HudShape)
                    .clickable { onPick() }
                    .padding(vertical = 12.dp),
            )
        }
    }
}
