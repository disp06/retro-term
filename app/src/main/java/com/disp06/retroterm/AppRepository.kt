package com.disp06.retroterm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class AppEntry(val label: String, val component: ComponentName) {
    val key: String = component.flattenToShortString()
    /** 24x24 ALPHA_8 pixel mask, filled in the background */
    @Volatile var icon: Bitmap? = null
}

/** Loads the launchable apps off the main thread and caches pixelated icons. */
object AppRepository {
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()
    private val iconCache = HashMap<String, Bitmap>() // touched only on exec thread

    var apps: List<AppEntry> = emptyList()
        private set

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun notifyListeners() = listeners.toList().forEach { it() }

    fun reload(ctx: Context, changedPackage: String? = null) {
        val app = ctx.applicationContext
        exec.execute {
            if (changedPackage != null) iconCache.keys.removeAll { it.startsWith("$changedPackage/") }
            val pm = app.packageManager
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved: List<Pair<AppEntry, ResolveInfo>> = pm.queryIntentActivities(query, 0)
                .asSequence()
                .filter { it.activityInfo.packageName != app.packageName }
                .map { ri ->
                    val label = (ri.loadLabel(pm)?.toString() ?: ri.activityInfo.packageName).trim().uppercase()
                    AppEntry(label, ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)) to ri
                }
                .sortedBy { it.first.label }
                .toList()
            resolved.forEach { (e, _) -> e.icon = iconCache[e.key] }
            val list = resolved.map { it.first }
            main.post { apps = list; notifyListeners() }

            var changed = false
            for ((e, ri) in resolved) {
                if (e.icon != null) continue
                val bmp = iconCache[e.key] ?: runCatching { IconPixelizer.pixelate(ri.loadIcon(pm)) }.getOrNull()
                if (bmp != null) { iconCache[e.key] = bmp; e.icon = bmp; changed = true }
            }
            if (changed) main.post { notifyListeners() }
        }
    }
}

/**
 * Turns any launcher icon into a 1-bit 24x24 pixel sprite:
 * silhouette outline + the "minority" luminance class (Otsu threshold) inside it,
 * so a white glyph on a coloured disc becomes a lit glyph inside a ring.
 */
object IconPixelizer {
    const val N = 24

    fun pixelate(d: Drawable): Bitmap {
        val big = Bitmap.createBitmap(N * 4, N * 4, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, N * 4, N * 4)
        d.draw(Canvas(big))
        val small = Bitmap.createScaledBitmap(big, N, N, true)
        val px = IntArray(N * N)
        small.getPixels(px, 0, N, 0, 0, N, N)
        if (small !== big) small.recycle()
        big.recycle()

        val opaque = BooleanArray(N * N)
        val lum = IntArray(N * N)
        val hist = IntArray(256)
        var nOpaque = 0
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a >= 128) {
                opaque[i] = true; nOpaque++
                val l = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
                lum[i] = l; hist[l]++
            }
        }

        val out = IntArray(N * N)
        if (nOpaque < 12) { // nothing usable: draw a box
            for (i in 0 until N) { out[i] = ON; out[(N - 1) * N + i] = ON; out[i * N] = ON; out[i * N + N - 1] = ON }
            return toMask(out)
        }

        val thr = otsu(hist, nOpaque)
        var bright = 0
        for (i in px.indices) if (opaque[i] && lum[i] > thr) bright++
        val litBright = bright <= nOpaque - bright // draw the smaller class (usually the glyph)
        val contrast = bright in (nOpaque / 30 + 1) until nOpaque

        for (y in 0 until N) for (x in 0 until N) {
            val i = y * N + x
            if (!opaque[i]) continue
            val edge = x == 0 || y == 0 || x == N - 1 || y == N - 1 ||
                !opaque[i - 1] || !opaque[i + 1] || !opaque[i - N] || !opaque[i + N]
            val glyph = contrast && ((lum[i] > thr) == litBright)
            if (edge || glyph) out[i] = ON
        }
        return toMask(out)
    }

    private const val ON = 0xFF000000.toInt()

    private fun toMask(out: IntArray): Bitmap =
        Bitmap.createBitmap(N, N, Bitmap.Config.ALPHA_8).apply { setPixels(out, 0, N, 0, 0, N, N) }

    private fun otsu(hist: IntArray, total: Int): Int {
        var sum = 0.0
        for (t in 0..255) sum += t * hist[t].toDouble()
        var sumB = 0.0; var wB = 0; var best = 0.0; var thr = 127
        for (t in 0..255) {
            wB += hist[t]; if (wB == 0) continue
            val wF = total - wB; if (wF == 0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; thr = t }
        }
        return thr
    }
}
