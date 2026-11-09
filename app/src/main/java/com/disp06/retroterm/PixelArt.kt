package com.disp06.retroterm

import android.graphics.Canvas
import android.graphics.Paint

/** Tiny 1-bit sprites drawn as rects so they stay crisp at any scale. */
object PixelArt {
    val STAR = arrayOf(
        "...#...",
        "...#...",
        "#######",
        ".#####.",
        "..###..",
        ".##.##.",
        ".#...#.",
    )

    private val CLOUD = arrayOf(
        "....####....",
        "..########..",
        ".##########.",
        "############",
        "############",
        ".##########.",
    )
    private val BLANK = "............"

    val SUN = arrayOf(
        ".....##.....",
        ".#...##...#.",
        "..#......#..",
        "....####....",
        "...######...",
        "##.######.##",
        "##.######.##",
        "...######...",
        "....####....",
        "..#......#..",
        ".#...##...#.",
        ".....##.....",
    )
    val PARTLY = arrayOf(
        "#.#.#.......",
        ".###........",
        "#####.......",
        ".###.####...",
        "#.#########.",
        "...#########",
        "..##########",
        "..##########",
        "...########.",
        BLANK, BLANK, BLANK,
    )
    val CLOUDY = arrayOf(BLANK, BLANK, BLANK) + CLOUD + arrayOf(BLANK, BLANK, BLANK)
    val RAIN = arrayOf(BLANK) + CLOUD + arrayOf(BLANK, ".#..#..#..#.", "#..#..#..#..", BLANK, ".#..#..#..#.")
    val SNOW = arrayOf(BLANK) + CLOUD + arrayOf(BLANK, ".#...#...#..", "###.###.###.", ".#...#...#..", BLANK)
    val STORM = arrayOf(BLANK) + CLOUD + arrayOf(".....##.....", "....##......", "...#####....", ".....##.....", "....#.......")
    val FOG = arrayOf(
        BLANK, "############", BLANK, ".##########.", BLANK, "############",
        BLANK, ".##########.", BLANK, "############", BLANK, BLANK,
    )

    fun weatherIcon(code: Int): Array<String> = when (code) {
        0 -> SUN
        1, 2 -> PARTLY
        3 -> CLOUDY
        45, 48 -> FOG
        in 51..67, in 80..82 -> RAIN
        in 71..77, 85, 86 -> SNOW
        in 95..99 -> STORM
        else -> CLOUDY
    }

    fun draw(c: Canvas, rows: Array<String>, x: Float, y: Float, px: Float, paint: Paint) {
        for (r in rows.indices) {
            val row = rows[r]
            for (i in row.indices) if (row[i] == '#') {
                c.drawRect(x + i * px, y + r * px, x + (i + 1) * px, y + (r + 1) * px, paint)
            }
        }
    }
}
