package com.oguzozsahin.ucusmodu.ui.window

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver

/**
 * Kullanicinin sectigi pencere videosu; sessiz, dongude ve cerceveyi tam dolduracak
 * sekilde kirpilarak oynatilir.
 *
 * Video uygulamanin icinde tasinmaz: cihazdaki dosyaya kalici okuma izniyle
 * baglanilir. Boylece hicbir telifli goruntu depoya ya da APK'ya girmez.
 */
@Composable
fun VideoWindow(
    uri: Uri,
    modifier: Modifier = Modifier,
    onFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val holder = remember { VideoHolder(context) }
    var aspect by remember { mutableFloatStateOf(0f) }

    SideEffect {
        holder.onAspect = { aspect = it }
        holder.onFailed = onFailed
    }

    LaunchedEffect(uri) { holder.setUri(uri) }

    // Uygulama arkaya alininca oynatma durur, one gelince devam eder.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> holder.pause()
                Lifecycle.Event.ON_RESUME -> holder.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.release()
        }
    }

    BoxWithConstraints(modifier.clipToBounds()) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val boxAspect = if (boxHeight.value > 0f) boxWidth / boxHeight else 1f

        // Ortadan kirparak doldur: kisa kenar cerceveye oturur, uzun kenar tasar.
        val width: Dp
        val height: Dp
        if (aspect <= 0f) {
            width = boxWidth
            height = boxHeight
        } else if (aspect > boxAspect) {
            height = boxHeight
            width = boxHeight * aspect
        } else {
            width = boxWidth
            height = boxWidth / aspect
        }

        Box(Modifier.align(Alignment.Center)) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                holder.attach(Surface(texture))
                            }

                            override fun onSurfaceTextureSizeChanged(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) = Unit

                            override fun onSurfaceTextureDestroyed(
                                texture: SurfaceTexture,
                            ): Boolean {
                                holder.detach()
                                return true
                            }

                            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier.size(width, height),
            )
        }
    }
}

/**
 * MediaPlayer'i yuzey ve adres hazir olunca kuran kucuk tutamak.
 * Compose tarafinda tek bir nesne olarak hatirlanir; boylece her yeniden
 * cizimde oynatici bastan kurulmaz.
 */
private class VideoHolder(private val context: Context) {

    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var source: Uri? = null

    var onAspect: (Float) -> Unit = {}
    var onFailed: () -> Unit = {}

    fun setUri(next: Uri) {
        if (source == next) return
        source = next
        start()
    }

    fun attach(next: Surface) {
        surface?.release()
        surface = next
        start()
    }

    fun detach() {
        release()
        surface?.release()
        surface = null
    }

    fun pause() {
        runCatching { player?.pause() }
    }

    fun resume() {
        runCatching { player?.start() }
    }

    fun release() {
        runCatching { player?.release() }
        player = null
    }

    private fun start() {
        release()
        val target = surface ?: return
        val uri = source ?: return
        try {
            player = MediaPlayer().apply {
                setSurface(target)
                setDataSource(context, uri)
                isLooping = true
                setVolume(0f, 0f)
                setOnPreparedListener { media ->
                    if (media.videoHeight > 0) {
                        onAspect(media.videoWidth.toFloat() / media.videoHeight.toFloat())
                    }
                    runCatching { media.start() }
                }
                setOnErrorListener { _, _, _ ->
                    onFailed()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            release()
            onFailed()
        }
    }
}
