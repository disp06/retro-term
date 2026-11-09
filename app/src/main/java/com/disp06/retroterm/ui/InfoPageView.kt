package com.disp06.retroterm.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.disp06.retroterm.AppEntry
import com.disp06.retroterm.BtInfo
import com.disp06.retroterm.PixelArt
import com.disp06.retroterm.WeatherRepo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Everything the INFO pages show. */
class InfoState(
    val weather: WeatherRepo.Snapshot? = null,
    val weatherBusy: Boolean = false,
    val weatherError: String? = null,
    val bt: BtInfo.State = BtInfo.State(null, emptyList()),
    val favorites: List<AppEntry> = emptyList(),
)

/**
 * One full-screen INFO page on the 454 grid.
 * kind 0 = WEATHER, 1 = BLUETOOTH, 2 = QUICK START (pinned apps).
 * Tap callback: (kind, index) — index = favorite cell 0..5, [KEY] for the bottom key, -1 elsewhere.
 */
class InfoPageView(ctx: Context, val kind: Int, private val onTap: (Int, Int) -> Unit) : View(ctx) {

    var palette: Palette = Palette.night()
    var state: InfoState = InfoState()
        set(v) { field = v; invalidate() }

    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(ctx) }
    private val fill = Paint()
    private val plain = Paint()
    private val iconPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val path = Path()
    private val dst = RectF()
    private val hhmm = SimpleDateFormat("HH:mm", Locale.US)

    private var s = 1f
    private var ox = 0f
    private var oy = 0f

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val x = (e.x - ox) / s; val y = (e.y - oy) / s
            val idx = when {
                y in 356f..398f && x in 140f..314f && kind != 0 -> KEY
                kind == 2 -> cellAt(x, y)
                else -> -1
            }
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onTap(kind, idx)
            return true
        }
    })

    override fun onTouchEvent(e: MotionEvent) = gestures.onTouchEvent(e) || super.onTouchEvent(e)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val side = min(w, h).toFloat()
        s = side / Ui.DESIGN; ox = (w - side) / 2f; oy = (h - side) / 2f
    }

    override fun onDraw(c: Canvas) {
        val p = palette
        c.drawColor(p.bg)
        c.save(); c.translate(ox, oy); c.scale(s, s)
        val glow = p.night
        if (glow) { text.setShadowLayer(7f, 0f, 0f, p.glow); fill.setShadowLayer(6f, 0f, 0f, p.glow) }
        else { text.clearShadowLayer(); fill.clearShadowLayer() }

        txt(c, TITLES[kind], 227f, 92f, Ui.T_BODY, p.fg, CENTER)
        dashes(c, 104f)
        // page dots
        for (i in 0 until 3) {
            plain.color = if (i == kind) p.fg else p.faint
            c.drawRect(428f, 207f + i * 16f, 436f, 215f + i * 16f, plain)
        }
        when (kind) {
            0 -> weather(c)
            1 -> bluetooth(c)
            else -> quick(c)
        }
        c.restore()
    }

    // ---------------------------------------------------------------- pages

    private fun weather(c: Canvas) {
        val p = palette
        val st = state
        val w = st.weather
        if (w == null) {
            if (st.weatherBusy) {
                txt(c, "SCANNING...", 227f, 220f, Ui.T_BODY, p.fg, CENTER)
                txt(c, "LOCATING + FETCHING", 227f, 256f, Ui.T_SMALL, p.dim, CENTER)
            } else {
                txt(c, "NO DATA", 227f, 210f, Ui.T_BODY, p.fg, CENTER)
                txt(c, st.weatherError ?: "", 227f, 246f, Ui.T_SMALL, p.dim, CENTER)
                txt(c, "TAP TO RETRY", 227f, 300f, Ui.T_SMALL, p.dim, CENTER)
            }
            return
        }
        fill.color = p.fg
        PixelArt.draw(c, w.icon, 96f, 126f, 6f, fill)
        txt(c, w.tempStr(), 180f, 192f, 54f, p.fg, LEFT)
        txt(c, w.long, 227f, 238f, Ui.T_BODY, p.fg, CENTER)
        dashes(c, 250f)
        row(c, "HI/LO", "${w.tempStr(w.hi).removeSuffix("°C")}/${w.tempStr(w.lo)}", 280f)
        row(c, "WIND", "${w.wind.roundToInt()} M/S", 310f)
        row(c, "HUMID", if (w.humidity >= 0) "${w.humidity}%" else "--", 340f)
        val foot = (if (w.place.isNotEmpty()) w.place.take(10) + " " else "") +
            if (st.weatherBusy) "SCAN.." else "UPD " + hhmm.format(Date(w.at))
        txt(c, foot, 227f, 374f, Ui.T_SMALL, p.dim, CENTER)
    }

    private fun bluetooth(c: Canvas) {
        val p = palette
        val bt = state.bt
        row(c, "RADIO", when (bt.radioOn) { true -> "ON"; false -> "OFF"; null -> "N/A" }, 140f)
        dashes(c, 152f)
        if (bt.devices.isEmpty()) {
            txt(c, if (bt.radioOn == false) "BLUETOOTH IS OFF" else "NO PAIRED DEVICES", 227f, 250f, Ui.T_SMALL, p.dim, CENTER)
        }
        bt.devices.take(5).forEachIndexed { i, d ->
            val y = 186f + i * 34f
            val half = chord(y - 10f)
            val l = 227f - half + 22f
            val r = 227f + half - 22f
            text.textSize = Ui.T_BODY
            val status = if (d.connected) "LINK" else "----"
            val nameW = r - l - (text.measureText(status) + 14f)
            val name = TextUtils.ellipsize(d.name, text, nameW, TextUtils.TruncateAt.END).toString()
            txt(c, name, l, y, Ui.T_BODY, if (d.connected) p.fg else p.dim, LEFT)
            txt(c, status, r, y, Ui.T_BODY, if (d.connected) p.fg else p.faint, RIGHT)
        }
        if (bt.devices.size > 5) txt(c, "+${bt.devices.size - 5} MORE", 227f, 348f, Ui.T_SMALL, p.dim, CENTER)
        key(c, "BT SETTINGS")
    }

    private fun quick(c: Canvas) {
        val p = palette
        val favs = state.favorites
        if (favs.isEmpty()) {
            txt(c, "NO PINNED APPS", 227f, 196f, Ui.T_BODY, p.fg, CENTER)
            txt(c, "LONG-PRESS ANY APP", 227f, 240f, Ui.T_SMALL, p.dim, CENTER)
            txt(c, "IN THE APP INDEX", 227f, 264f, Ui.T_SMALL, p.dim, CENTER)
            txt(c, "TO PIN IT HERE", 227f, 288f, Ui.T_SMALL, p.dim, CENTER)
        }
        favs.take(6).forEachIndexed { i, e ->
            val cx = CELL_X[i % 3]; val top = CELL_Y[i / 3]
            notch(cx - 46f, top, cx + 46f, top + 108f, 6f)
            fill.color = p.fg
            plain.color = p.faint
            val strokeP = Paint(plain).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = p.dim }
            c.drawPath(path, strokeP)
            dst.set(cx - 24f, top + 12f, cx + 24f, top + 60f)
            val icon = e.icon
            if (icon != null) { iconPaint.color = p.fg; c.drawBitmap(icon, null, dst, iconPaint) }
            else c.drawRect(dst.left + 8f, dst.top + 8f, dst.right - 8f, dst.bottom - 8f, fill)
            text.textSize = Ui.T_SMALL
            val label = TextUtils.ellipsize(e.label, text, 84f, TextUtils.TruncateAt.END).toString()
            txt(c, label, cx, top + 92f, Ui.T_SMALL, p.fg, CENTER)
        }
        if (favs.size > 6) txt(c, "+${favs.size - 6} MORE IN LIST", 227f, 352f, Ui.T_SMALL, p.dim, CENTER)
        key(c, "ALL APPS")
    }

    private fun cellAt(x: Float, y: Float): Int {
        val n = min(6, state.favorites.size)
        for (i in 0 until n) {
            val cx = CELL_X[i % 3]; val top = CELL_Y[i / 3]
            if (x in cx - 50f..cx + 50f && y in top - 4f..top + 112f) return i
        }
        return -1
    }

    // ---------------------------------------------------------------- helpers

    private fun key(c: Canvas, label: String) {
        val p = palette
        text.textSize = Ui.T_SMALL
        val w = text.measureText(label) - 2f
        val l = 227f - w / 2f - 14f; val r = 227f + w / 2f + 14f
        notch(l, 362f, r, 392f, 5f)
        fill.color = p.fg
        c.drawPath(path, fill)
        txt(c, label, 227f, 384f, Ui.T_SMALL, p.bg, CENTER, glow = false)
    }

    private fun row(c: Canvas, label: String, value: String, baseline: Float) {
        txt(c, label, 84f, baseline, Ui.T_BODY, palette.dim, LEFT)
        txt(c, value, 370f, baseline, Ui.T_BODY, palette.fg, RIGHT)
    }

    private fun chord(y: Float): Float { val dy = y - 227f; return sqrt(maxOf(0f, 200f * 200f - dy * dy)) }

    private fun dashes(c: Canvas, y: Float) {
        val half = chord(y) - 10f
        plain.color = palette.dim
        var x = 227f - half
        while (x < 227f + half) { c.drawRect(x, y, min(x + 8f, 227f + half), y + 2f, plain); x += 12f }
    }

    private fun notch(l: Float, t: Float, r: Float, b: Float, k: Float) {
        path.reset()
        path.moveTo(l + k, t); path.lineTo(r - k, t); path.lineTo(r, t + k); path.lineTo(r, b - k)
        path.lineTo(r - k, b); path.lineTo(l + k, b); path.lineTo(l, b - k); path.lineTo(l, t + k); path.close()
    }

    private fun txt(c: Canvas, str: String, x: Float, baseline: Float, size: Float, color: Int, align: Int, glow: Boolean = true) {
        if (str.isEmpty()) return
        text.textSize = size
        text.color = color
        val w = text.measureText(str) - size / 9f
        val sx = when (align) { LEFT -> x; CENTER -> x - w / 2f; else -> x - w }
        if (!glow && palette.night) {
            text.clearShadowLayer(); c.drawText(str, sx.roundToInt().toFloat(), baseline, text)
            text.setShadowLayer(7f, 0f, 0f, palette.glow)
        } else c.drawText(str, sx.roundToInt().toFloat(), baseline, text)
    }

    companion object {
        const val KEY = 99
        private const val LEFT = -1
        private const val CENTER = 0
        private const val RIGHT = 1
        private val TITLES = arrayOf("[ WEATHER ]", "[ BLUETOOTH ]", "[ QUICK START ]")
        private val CELL_X = floatArrayOf(123f, 227f, 331f)
        private val CELL_Y = floatArrayOf(118f, 238f)
    }
}
