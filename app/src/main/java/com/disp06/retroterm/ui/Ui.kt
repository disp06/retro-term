package com.disp06.retroterm.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.disp06.retroterm.R
import kotlin.math.min

/** Shared design constants. Everything is drawn on a 454x454 grid (TicWatch Pro 3 native). */
object Ui {
    const val DESIGN = 454f

    /** Monocraft pixel font: 1 font-pixel = size/9 (5x7 glyphs, 6-px advance), so multiples of 9 render crisp. */
    const val T_SMALL = 18f
    const val T_BODY = 27f
    const val T_CLOCK = 81f

    fun scale(ctx: Context): Float {
        val dm = ctx.resources.displayMetrics
        return min(dm.widthPixels, dm.heightPixels) / DESIGN
    }

    @Volatile private var font: Typeface? = null

    fun font(ctx: Context): Typeface =
        font ?: (runCatching { ResourcesCompat.getFont(ctx.applicationContext, R.font.monocraft) }.getOrNull()
            ?: Typeface.MONOSPACE).also { font = it }
}

/** Active colour set. Built from the current Day/Night theme attributes. */
class Palette(val bg: Int, val fg: Int, val night: Boolean) {
    val dim: Int = blend(bg, fg, 0.55f)
    val faint: Int = blend(bg, fg, 0.22f)
    /** Phosphor glow colour (night only) */
    val glow: Int = (fg and 0x00FFFFFF) or (0xA0 shl 24)

    companion object {
        fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        )

        fun night() = Palette(0xFF000000.toInt(), 0xFF39FF14.toInt(), true)
        fun day() = Palette(0xFFFFB000.toInt(), 0xFF111111.toInt(), false)
        fun forNight(night: Boolean) = if (night) night() else day()

        fun fromTheme(ctx: Context): Palette {
            val ta = ctx.obtainStyledAttributes(R.styleable.TermTheme)
            try {
                val night = ta.getBoolean(R.styleable.TermTheme_termNight, true)
                val def = if (night) night() else day()
                return Palette(
                    ta.getColor(R.styleable.TermTheme_termBg, def.bg),
                    ta.getColor(R.styleable.TermTheme_termFg, def.fg),
                    night
                )
            } finally {
                ta.recycle()
            }
        }
    }
}
