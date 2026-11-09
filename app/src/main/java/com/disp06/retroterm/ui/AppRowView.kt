package com.disp06.retroterm.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import com.disp06.retroterm.AppEntry
import com.disp06.retroterm.PixelArt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One terminal menu line:  "07 [icon] SETTINGS"  /  selected: "> [icon] SETTINGS" inverted.
 * Drawn directly (no TextView layout) and follows the round screen edge: the left/right
 * inset is recomputed from the row's distance to the screen centre on every scroll frame.
 */
class AppRowView(ctx: Context, private val s: Float) : View(ctx) {

    var entry: AppEntry? = null
    var index = 0
    var palette: Palette = Palette.night()
    var fav = false

    private val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(ctx); textSize = Ui.T_BODY * s }
    private val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(ctx); textSize = Ui.T_SMALL * s }
    private val fill = Paint()
    private val iconPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val path = Path()
    private val dst = RectF()

    private var cachedLabel: CharSequence = ""
    private var cachedFor: String? = null
    private var cachedWidth = -1

    fun bind(e: AppEntry, i: Int, p: Palette, favorite: Boolean) {
        entry = e; index = i; palette = p; fav = favorite
        cachedFor = null
        invalidate()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val e = entry ?: return
        val parent = parent as? View ?: return
        val p = palette
        val h = height.toFloat()

        // --- round-screen inset from this row's vertical position
        val rad = parent.width / 2f
        val cy = top + translationY + h / 2f
        val dy = cy - parent.height / 2f
        val rr = rad * 0.97f
        val half = sqrt(max(0f, rr * rr - dy * dy))
        val inset = max(12f * s, rad - half + 8f * s)
        val left = inset
        val right = width - inset
        if (right - left < 60f * s) return

        val selected = abs(dy) < h / 2f || isPressed
        val fg = if (selected) p.bg else p.fg

        fill.clearShadowLayer()
        if (selected) {
            val k = 6f * s
            val t = 5f * s; val b = h - 5f * s
            val l = left - 6f * s; val r = right + 6f * s
            path.reset()
            path.moveTo(l + k, t); path.lineTo(r - k, t); path.lineTo(r, t + k); path.lineTo(r, b - k)
            path.lineTo(r - k, b); path.lineTo(l + k, b); path.lineTo(l, b - k); path.lineTo(l, t + k); path.close()
            fill.color = p.fg
            c.drawPath(path, fill)
        } else {
            // dashed separator
            fill.color = p.faint
            var x = left
            val y = h - 2f * s
            while (x < right) { c.drawRect(x, y, minOf(x + 8f * s, right), y + 2f * s, fill); x += 12f * s }
        }

        val glow = p.night && !selected
        if (glow) fill.setShadowLayer(5f * s, 0f, 0f, p.glow) else fill.clearShadowLayer()
        if (glow) { name.setShadowLayer(6f * s, 0f, 0f, p.glow); small.setShadowLayer(5f * s, 0f, 0f, p.glow) }
        else { name.clearShadowLayer(); small.clearShadowLayer() }

        var x = left + 4f * s
        val base = h / 2f + 11f * s
        if (selected) {
            name.color = fg
            c.drawText(">", x, base, name)
        } else {
            small.color = p.dim
            c.drawText(if (index < 9) "0${index + 1}" else "${index + 1}", x, h / 2f + 7f * s, small)
        }
        x += 30f * s

        // pixel icon 24 -> 48
        val icon = e.icon
        val isz = 48f * s
        dst.set(x, (h - isz) / 2f, x + isz, (h + isz) / 2f)
        if (icon != null) {
            iconPaint.color = fg
            c.drawBitmap(icon, null, dst, iconPaint)
        } else {
            fill.color = if (selected) p.bg else p.faint
            c.drawRect(dst.left + 8f * s, dst.top + 8f * s, dst.right - 8f * s, dst.bottom - 8f * s, fill)
        }
        x += isz + 12f * s

        // pinned: pixel star at the right end
        var nameRight = right - 4f * s
        if (fav) {
            val px = 3f * s
            fill.color = fg
            PixelArt.draw(c, PixelArt.STAR, right - 6f * s - 7 * px, (h - 7 * px) / 2f, px, fill)
            nameRight -= 7 * px + 10f * s
        }

        val avail = (nameRight - x).toInt()
        if (avail <= 0) return
        if (cachedFor != e.key || cachedWidth != avail) {
            cachedLabel = TextUtils.ellipsize(e.label, name, avail.toFloat(), TextUtils.TruncateAt.END)
            cachedFor = e.key; cachedWidth = avail
        }
        name.color = fg
        c.drawText(cachedLabel, 0, cachedLabel.length, x, base, name)
    }
}
