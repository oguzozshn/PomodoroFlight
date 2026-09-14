package com.oguzozsahin.ucusmodu.ui.window

import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Pencere goruntusundeki kara parcalarinin ufka oturtulmasi ve kirpilmasi.
 *
 * Compose'a hic dokunmuyor: [landPath] cizimi yapiyor, buradaki iki islem ise
 * yalnizca sayi kumeleri uzerinde calisiyor ve bu sayede cihazsiz
 * sinanabiliyor. Ayni dosyada durduklari surece sinanamiyorlardi ve
 * ikisi de uygulamanin en ince geometrisi.
 */

/** Ufuk cemberinde iki nokta arasi bu aciyi asarsa araya yay noktalari konur. */
internal const val CAP_ARC_STEP_RAD = 0.05

/** Ufka bastirilmis iki komsu nokta bundan yakinsa ikincisi atlanir. */
internal const val CAP_MERGE_RAD = 0.008

/**
 * Halkayi ufuk cemberine bastirir ve ayni gecicte seyreltir.
 *
 * Ufkun otesindeki nokta atilmaz, bulundugu yonde cembere tasinir: halka kapali
 * ve ayni yonde donen tek bir egri olarak kalir, bu yuzden korfezler kara
 * sanilmaz. Kirpmayla yapilan cozumde halkanin iki ucunu birlestiren kiris
 * Ege'yi bastan basa kara yapiyordu.
 *
 * Arkada kalan kiyilarin binlerce noktasi cemberin ayni yerine dustugu icin
 * birbirine cok yakin dusenler atlanir; arada buyuk bir sicrama varsa da yay
 * uzerine ara nokta konur. Boylece bir kitanin on bin noktasi birkac yuze iner.
 */
internal fun clampToHorizon(
    src: FloatArray,
    count: Int,
    out: DoubleArray,
    ux: Double,
    uy: Double,
    uz: Double,
    cap: Double,
): Int {
    val ring = sqrt((1.0 - cap * cap).coerceAtLeast(0.0))
    val capacity = out.size / 3
    var written = 0

    // En son cembere konan noktanin yonu
    var hasPrevious = false
    var px = 0.0
    var py = 0.0
    var pz = 0.0

    for (i in 0 until count) {
        val x = src[i * 3].toDouble()
        val y = src[i * 3 + 1].toDouble()
        val z = src[i * 3 + 2].toDouble()
        val height = x * ux + y * uy + z * uz

        if (height >= cap) {
            if (written >= capacity) return -1
            out[written * 3] = x
            out[written * 3 + 1] = y
            out[written * 3 + 2] = z
            written++
            hasPrevious = false
            continue
        }

        // Noktanin ufuk duzlemindeki yonu; buyuklugu bir yapilip cembere tasinir.
        var wx = x - height * ux
        var wy = y - height * uy
        var wz = z - height * uz
        val length = sqrt(wx * wx + wy * wy + wz * wz)
        if (length < 1e-12) continue
        wx /= length
        wy /= length
        wz /= length

        if (hasPrevious) {
            val dot = (px * wx + py * wy + pz * wz).coerceIn(-1.0, 1.0)
            val angle = acos(dot)
            if (angle < CAP_MERGE_RAD) continue
            if (angle > CAP_ARC_STEP_RAD) {
                // Yayin ikinci ekseni: onceki noktaya dik, bu noktaya bakan yon
                var ax = wx - dot * px
                var ay = wy - dot * py
                var az = wz - dot * pz
                val alen = sqrt(ax * ax + ay * ay + az * az)
                if (alen > 1e-12) {
                    ax /= alen
                    ay /= alen
                    az /= alen
                    // Yukari yuvarlaniyor: asagi yuvarlanınca 2,7 radyanlik
                    // bir yay 53 parcaya bolunup adim 0,051'e cikiyordu, yani
                    // istenen cozunurluk aslinda hic tutturulmuyordu.
                    val steps = ceil(angle / CAP_ARC_STEP_RAD).toInt().coerceAtMost(CAP_ARC_LIMIT)
                    for (k in 1 until steps) {
                        val t = angle * k / steps
                        val c = cos(t)
                        val sn = sin(t)
                        if (written >= capacity) return -1
                        out[written * 3] = cap * ux + ring * (px * c + ax * sn)
                        out[written * 3 + 1] = cap * uy + ring * (py * c + ay * sn)
                        out[written * 3 + 2] = cap * uz + ring * (pz * c + az * sn)
                        written++
                    }
                }
            }
        }

        if (written >= capacity) return -1
        out[written * 3] = cap * ux + ring * wx
        out[written * 3 + 1] = cap * uy + ring * wy
        out[written * 3 + 2] = cap * uz + ring * wz
        written++
        hasPrevious = true
        px = wx
        py = wy
        pz = wz
    }
    return written
}

/** Tek bir yayin en fazla kac parcaya bolunecegi. */
internal const val CAP_ARC_LIMIT = 160

/**
 * Sutherland-Hodgman: kapali bir halkayi tek bir yari uzaya kirpar.
 * [inside] pozitif donerse nokta icerdedir; sinir uzerinde dogrusal enterpolasyon
 * yapilir. Cikti tamponu yetmezse -1 doner: yarim kalmis bir halka cizilmez.
 */
internal inline fun clipToHalfSpace(
    src: DoubleArray,
    count: Int,
    out: DoubleArray,
    inside: (Double, Double, Double) -> Double,
): Int {
    val capacity = out.size / 3
    var written = 0
    val last = count - 1
    var prevX = src[last * 3]
    var prevY = src[last * 3 + 1]
    var prevZ = src[last * 3 + 2]
    var prevValue = inside(prevX, prevY, prevZ)

    for (i in 0 until count) {
        val x = src[i * 3]
        val y = src[i * 3 + 1]
        val z = src[i * 3 + 2]
        val value = inside(x, y, z)

        if (value >= 0.0) {
            if (prevValue < 0.0) {
                if (written >= capacity) return -1
                val t = prevValue / (prevValue - value)
                out[written * 3] = prevX + (x - prevX) * t
                out[written * 3 + 1] = prevY + (y - prevY) * t
                out[written * 3 + 2] = prevZ + (z - prevZ) * t
                written++
            }
            if (written >= capacity) return -1
            out[written * 3] = x
            out[written * 3 + 1] = y
            out[written * 3 + 2] = z
            written++
        } else if (prevValue >= 0.0) {
            if (written >= capacity) return -1
            val t = prevValue / (prevValue - value)
            out[written * 3] = prevX + (x - prevX) * t
            out[written * 3 + 1] = prevY + (y - prevY) * t
            out[written * 3 + 2] = prevZ + (z - prevZ) * t
            written++
        }

        prevX = x
        prevY = y
        prevZ = z
        prevValue = value
    }
    return written
}
