package com.disp06.retroterm.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

/** Static frame for the app index: header/footer bands, double rules and centre selector arrows. */
class DrawerChromeView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var palette: Palette = Palette.night()
        set(v) { field = v; invalidate() }

    private val fill = Paint()
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val side = min(width, height).toFloat()
        val s = side / Ui.DESIGN
        val p = palette
        c.save()
        c.translate((width - side) / 2f, (height - side) / 2f)
        c.scale(s, s)

        fill.color = p.bg
        c.drawRect(0f, 0f, 454f, TOP, fill)
        c.drawRect(0f, BOTTOM, 454f, 454f, fill)

        rule(c, TOP - 6f, 3f, p.fg, solid = true)
        rule(c, TOP - 1f, 2f, p.dim, solid = false)
        rule(c, BOTTOM + 3f, 3f, p.fg, solid = true)
        rule(c, BOTTOM - 1f, 2f, p.dim, solid = false)

        // selector arrows at the screen edge, pointing at the centred row
        fill.color = p.fg
        if (p.night) fill.setShadowLayer(6f, 0f, 0f, p.glow) else fill.clearShadowLayer()
        path.reset()
        path.moveTo(4f, 215f); path.lineTo(14f, 227f); path.lineTo(4f, 239f); path.close()
        path.moveTo(450f, 215f); path.lineTo(440f, 227f); path.lineTo(450f, 239f); path.close()
        c.drawPath(path, fill)
        fill.clearShadowLayer()
        c.restore()
    }

    private fun rule(c: Canvas, y: Float, th: Float, color: Int, solid: Boolean) {
        val dy = y - 227f
        val half = sqrt(maxOf(0f, 214f * 214f - dy * dy))
        val x0 = 227f - half; val x1 = 227f + half
        fill.color = color
        if (solid) c.drawRect(x0, y, x1, y + th, fill)
        else { var x = x0; while (x < x1) { c.drawRect(x, y, min(x + 6f, x1), y + th, fill); x += 10f } }
    }

    companion object {
        const val TOP = 94f
        const val BOTTOM = 362f
    }
}
