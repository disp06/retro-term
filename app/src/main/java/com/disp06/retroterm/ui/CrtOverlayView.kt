package com.disp06.retroterm.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** CRT glass: horizontal scanlines + edge vignette. Pure decoration, never takes touches. */
class CrtOverlayView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var palette: Palette = Palette.night()
        set(v) { field = v; rebuild() }

    private val scan = Paint()
    private val vignette = Paint()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rebuild()

    private fun rebuild() {
        if (width == 0) return
        val s = min(width, height) / Ui.DESIGN
        val period = max(2, (3 * s).roundToInt())
        val bmp = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, if (palette.night) Color.argb(110, 0, 0, 0) else Color.argb(34, 0, 0, 0))
        scan.shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

        val edge = if (palette.night) Color.argb(200, 0, 0, 0) else Color.argb(120, 60, 20, 0)
        vignette.shader = RadialGradient(
            width / 2f, height / 2f, min(width, height) / 2f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, edge),
            floatArrayOf(0f, 0.78f, 1f), Shader.TileMode.CLAMP
        )
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scan)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignette)
    }
}
