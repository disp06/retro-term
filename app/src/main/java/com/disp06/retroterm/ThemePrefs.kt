package com.disp06.retroterm

import android.content.Context
import java.util.Calendar

enum class ThemeMode(val tag: String) { AUTO("AUT"), DAY("DAY"), NIGHT("NGT") }

/** Persists the Day / Night / Auto choice. AUTO = amber 07:00-19:59, green otherwise. */
object ThemePrefs {
    private const val FILE = "retroterm"
    private const val KEY = "theme_mode"
    const val DAY_START = 7
    const val DAY_END = 20

    fun mode(ctx: Context): ThemeMode {
        val name = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, ThemeMode.AUTO.name)
        return runCatching { ThemeMode.valueOf(name!!) }.getOrDefault(ThemeMode.AUTO)
    }

    fun cycle(ctx: Context): ThemeMode {
        val next = when (mode(ctx)) {
            ThemeMode.AUTO -> ThemeMode.DAY
            ThemeMode.DAY -> ThemeMode.NIGHT
            ThemeMode.NIGHT -> ThemeMode.AUTO
        }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, next.name).apply()
        return next
    }

    fun isNight(mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.DAY -> false
        ThemeMode.NIGHT -> true
        ThemeMode.AUTO -> Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it < DAY_START || it >= DAY_END }
    }
}
