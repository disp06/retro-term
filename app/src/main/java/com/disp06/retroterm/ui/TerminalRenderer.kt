package com.disp06.retroterm.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.text.format.DateFormat
import com.disp06.retroterm.Telemetry
import com.disp06.retroterm.WeatherRepo
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The terminal clock screen, drawn on a 454x454 grid. Used by the watch face.
 *
 *   y  70- 98  header : battery cell + %, theme key            (tap -> cycle AUT/DAY/NGT)
 *   y 116-202  CHRONO : HH:MM, seconds + AM/PM tags in border  (tap -> INFO screen)
 *   y 215-236  date
 *   y 256-341  STATUS (unread count) / WEATHER (or COMM LINK) / SIGNAL
 *   y 350-374  spectrum strip
 *   y 384-416  [ APPS ] key                                     (tap -> app index)
 */
class TerminalRenderer(ctx: Context) {

    enum class Zone { NONE, THEME, INFO, APPS }

    var palette: Palette = Palette.night()
        set(v) { if (v.night != field.night || v.bg != field.bg) { field = v; applyPaints(); crtKey = -1 } }
    var modeTag = "AUT"
    var telemetry: Telemetry? = null
    var weather: WeatherRepo.Snapshot? = null
    var unread = 0
    var is24 = DateFormat.is24HourFormat(ctx)

    var ambient = false
        set(v) { field = v; applyPaints() }
    var lowBit = false
        set(v) { field = v; applyPaints() }
    var burnIn = false

    private val cal: Calendar = Calendar.getInstance()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(ctx) }
    private val fill = Paint().apply { style = Paint.Style.FILL }
    private val line = Paint().apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.SQUARE; strokeJoin = Paint.Join.MITER }
    private val plain = Paint().apply { style = Paint.Style.FILL }
    private val path = Path()

    private val scan = Paint()
    private val vignette = Paint()
    private var crtKey = -1

    init { applyPaints() }

    fun setTime(ms: Long) { cal.timeInMillis = ms }

    fun hit(x: Float, y: Float, w: Int, h: Int): Zone {
        val side = min(w, h).toFloat()
        val dy = (y - (h - side) / 2f) / (side / Ui.DESIGN)
        return when {
            dy <= 104f -> Zone.THEME
            dy >= 368f -> Zone.APPS
            else -> Zone.INFO
        }
    }

    private fun applyPaints() {
        val glow = palette.night && !ambient
        if (glow) {
            text.setShadowLayer(7f, 0f, 0f, palette.glow)
            fill.setShadowLayer(7f, 0f, 0f, palette.glow)
            line.setShadowLayer(7f, 0f, 0f, palette.glow)
        } else {
            text.clearShadowLayer(); fill.clearShadowLayer(); line.clearShadowLayer()
        }
        text.isAntiAlias = !(ambient && lowBit)
    }

    fun draw(c: Canvas, w: Int, h: Int) {
        val side = min(w, h).toFloat()
        val s = side / Ui.DESIGN
        c.drawColor(if (ambient) Color.BLACK else palette.bg)
        c.save()
        c.translate((w - side) / 2f, (h - side) / 2f)
        c.scale(s, s)
        if (ambient) drawAmbient(c) else drawActive(c)
        c.restore()
        if (!ambient) drawCrt(c, w, h, s)
    }

    // ---------------------------------------------------------------- CRT glass

    private fun drawCrt(c: Canvas, w: Int, h: Int, s: Float) {
        val key = w * 31 + h + if (palette.night) 1 else 0
        if (key != crtKey) {
            crtKey = key
            val period = max(2, (3 * s).roundToInt())
            val bmp = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888)
            bmp.setPixel(0, 0, if (palette.night) Color.argb(110, 0, 0, 0) else Color.argb(34, 0, 0, 0))
            scan.shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val edge = if (palette.night) Color.argb(200, 0, 0, 0) else Color.argb(120, 60, 20, 0)
            vignette.shader = RadialGradient(
                w / 2f, h / 2f, min(w, h) / 2f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, edge), floatArrayOf(0f, 0.78f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), scan)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignette)
    }

    // ---------------------------------------------------------------- interactive

    private fun drawActive(c: Canvas) {
        val p = palette
        val t = telemetry
        val sec = cal.get(Calendar.SECOND)
        val blink = sec % 2 == 0

        // seconds ring
        for (i in 0 until 60) {
            val major = i % 5 == 0
            plain.color = if (i <= sec) p.fg else p.faint
            c.save()
            c.rotate(i * 6f, 227f, 227f)
            if (major) c.drawRect(224f, 5f, 230f, 23f, plain) else c.drawRect(225f, 7f, 229f, 17f, plain)
            c.restore()
        }

        // header
        val batt = t?.battery ?: -1
        line.color = p.fg; line.strokeWidth = 3f
        c.drawRect(113.5f, 75.5f, 149.5f, 95.5f, line)
        fill.color = p.fg
        c.drawRect(151f, 81f, 155f, 90f, fill)
        val segs = if (batt < 0) 0 else ((batt + 19) / 20).coerceIn(0, 5)
        for (k in 0 until 5) {
            fill.color = if (k < segs) p.fg else p.faint
            c.drawRect(117f + k * 6.4f, 79f, 117f + k * 6.4f + 4.4f, 92f, fill)
        }
        val battStr = if (batt < 0) "--%" else "$batt%"
        txt(c, battStr, 163f, 96f, Ui.T_BODY, p.fg, LEFT)
        if (t?.charging == true && blink) txt(c, "+", 163f + width(battStr, Ui.T_BODY) + 4f, 96f, Ui.T_BODY, p.fg, LEFT)

        notch(276f, 72f, 344f, 100f, 5f)
        line.strokeWidth = 3f
        c.drawPath(path, line)
        txt(c, modeTag, 310f, 96f, Ui.T_BODY, p.fg, CENTER)

        // CHRONO
        notch(70f, 116f, 384f, 202f, 10f)
        line.strokeWidth = 4f; line.color = p.fg
        c.drawPath(path, line)
        tag(c, "CHRONO", 86f, 110f, 124f)
        tag(c, two(sec), 334f, 194f, 209f)
        if (!is24) tag(c, if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM", 86f, 194f, 209f)

        val hour = if (is24) cal.get(Calendar.HOUR_OF_DAY) else cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        txt(c, two(hour) + ":" + two(cal.get(Calendar.MINUTE)), 227f, 194f, Ui.T_CLOCK, p.fg, CENTER, snap = true)

        txt(c, dateStr(true), 227f, 236f, Ui.T_BODY, p.fg, CENTER)
        dashes(c, 66f, 388f, 247f, p.dim)

        // telemetry rows
        val l = 74f; val r = 380f
        txt(c, "STATUS", l, 278f, Ui.T_BODY, p.dim, LEFT)
        val status = when {
            unread > 99 -> "99+ MSG"
            unread > 0 -> "$unread MSG"
            t?.charging == true -> "CHARGE"
            else -> "ACTIVE"
        }
        txt(c, status, r - 16f, 278f, Ui.T_BODY, p.fg, RIGHT)
        if (blink || unread > 0) { fill.color = p.fg; if (blink) c.drawRect(r - 9f, 257f, r, 278f, fill) }

        val wx = weather
        if (wx != null && System.currentTimeMillis() - wx.at < 3 * 3600_000L) {
            txt(c, "WEATHER", l, 308f, Ui.T_BODY, p.dim, LEFT)
            txt(c, wx.tempStr() + " " + wx.short, r, 308f, Ui.T_BODY, p.fg, RIGHT)
        } else {
            txt(c, "COMM LINK", l, 308f, Ui.T_BODY, p.dim, LEFT)
            val comm = t?.commTenths ?: 784
            txt(c, "${comm / 10}.${comm % 10}MHz", r, 308f, Ui.T_BODY, p.fg, RIGHT)
        }

        txt(c, if (t?.signalReal == true) "WIFI SIG" else "SIGNAL", l, 338f, Ui.T_BODY, p.dim, LEFT)
        val sig = t?.signal ?: 0
        fill.color = p.fg
        val bx = r - 116f
        c.drawRect(bx, 317f, bx + 3f, 338f, fill); c.drawRect(bx, 317f, bx + 8f, 320f, fill); c.drawRect(bx, 335f, bx + 8f, 338f, fill)
        c.drawRect(r - 3f, 317f, r, 338f, fill); c.drawRect(r - 8f, 317f, r, 320f, fill); c.drawRect(r - 8f, 335f, r, 338f, fill)
        for (k in 0 until 8) {
            val x0 = bx + 10f + k * 12f
            if (k < sig) { fill.color = p.fg; c.drawRect(x0, 322f, x0 + 8f, 334f, fill) }
            else { plain.color = p.faint; c.drawRect(x0, 322f, x0 + 8f, 334f, plain) }
        }

        // spectrum
        plain.color = p.faint
        c.drawRect(98f, 374f, 356f, 376f, plain)
        t?.spectrum?.let { spec ->
            plain.color = p.fg
            for (k in spec.indices) {
                val hh = spec[k] * 2f
                val x0 = 99f + k * 8f
                c.drawRect(x0, 372f - hh, x0 + 6f, 372f, plain)
            }
        }

        // APPS key
        notch(156f, 384f, 298f, 416f, 6f)
        fill.color = p.fg
        c.drawPath(path, fill)
        txt(c, "APPS", 227f, 411f, Ui.T_BODY, p.bg, CENTER, glow = false)
    }

    // ---------------------------------------------------------------- ambient

    private fun drawAmbient(c: Canvas) {
        val fg = if (palette.night) AMBIENT_GREEN else AMBIENT_AMBER
        val m = cal.get(Calendar.MINUTE)
        val shift = if (burnIn) ((m % 5) - 2) * 4f else 0f
        c.translate(shift, -shift)
        val hour = if (is24) cal.get(Calendar.HOUR_OF_DAY) else cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        text.style = if (burnIn) Paint.Style.STROKE else Paint.Style.FILL
        text.strokeWidth = 2f
        txt(c, two(hour) + ":" + two(m), 227f, 262f, Ui.T_CLOCK, fg, CENTER, snap = true)
        text.style = Paint.Style.FILL
        txt(c, dateStr(false), 227f, 306f, Ui.T_BODY, fg, CENTER)
        val batt = telemetry?.battery ?: -1
        val top = if (unread > 0) "MSG $unread  BATT $batt%" else if (batt < 0) "BATT --%" else "BATT $batt%"
        txt(c, top, 227f, 164f, Ui.T_BODY, fg, CENTER)
    }

    // ---------------------------------------------------------------- helpers

    private fun dateStr(year: Boolean): String {
        val d = DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1] + " " + two(cal.get(Calendar.DAY_OF_MONTH)) + "." + two(cal.get(Calendar.MONTH) + 1)
        return if (year) d + "." + cal.get(Calendar.YEAR) else d
    }

    /** small label cut into a frame border */
    private fun tag(c: Canvas, str: String, x: Float, top: Float, baseline: Float) {
        plain.color = palette.bg
        c.drawRect(x, top, x + width(str, Ui.T_SMALL) + 12f, top + 16f, plain)
        txt(c, str, x + 6f, baseline, Ui.T_SMALL, palette.fg, LEFT)
    }

    private fun width(str: String, size: Float): Float {
        text.textSize = size
        return text.measureText(str) - size / 9f
    }

    private fun txt(
        c: Canvas, str: String, x: Float, baseline: Float, size: Float, color: Int, align: Int,
        glow: Boolean = true, snap: Boolean = false
    ) {
        text.textSize = size
        text.color = color
        val w = text.measureText(str) - size / 9f
        val sx = when (align) { LEFT -> x; CENTER -> x - w / 2f; else -> x - w }
        val px = size / 9f
        val gx = if (snap) (sx / px).roundToInt() * px else sx.roundToInt().toFloat()
        if (!glow && palette.night && !ambient) {
            text.clearShadowLayer()
            c.drawText(str, gx, baseline, text)
            text.setShadowLayer(7f, 0f, 0f, palette.glow)
        } else {
            c.drawText(str, gx, baseline, text)
        }
    }

    private fun notch(l: Float, t: Float, r: Float, b: Float, k: Float) {
        path.reset()
        path.moveTo(l + k, t); path.lineTo(r - k, t); path.lineTo(r, t + k); path.lineTo(r, b - k)
        path.lineTo(r - k, b); path.lineTo(l + k, b); path.lineTo(l, b - k); path.lineTo(l, t + k); path.close()
    }

    private fun dashes(c: Canvas, x0: Float, x1: Float, y: Float, color: Int) {
        plain.color = color
        var x = x0
        while (x < x1) { c.drawRect(x, y, min(x + 8f, x1), y + 2f, plain); x += 12f }
    }

    private fun two(v: Int) = if (v < 10) "0$v" else v.toString()

    companion object {
        private const val LEFT = -1
        private const val CENTER = 0
        private const val RIGHT = 1
        private val DAYS = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        private const val AMBIENT_GREEN = 0xFF1E9E0A.toInt()
        private const val AMBIENT_AMBER = 0xFFB07800.toInt()
    }
}
