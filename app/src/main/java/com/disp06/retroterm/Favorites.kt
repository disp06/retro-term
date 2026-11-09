package com.disp06.retroterm

import android.content.Context

/** Pinned ("starred") apps, stored as component keys. Pinned apps sort to the top of the list. */
object Favorites {
    private const val FILE = "retroterm"
    private const val KEY = "favs"

    fun get(ctx: Context): Set<String> =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet(KEY, emptySet())!!.toSet()

    /** @return true if the app is now pinned */
    fun toggle(ctx: Context, key: String): Boolean {
        val set = get(ctx).toMutableSet()
        val pinned = if (key in set) { set.remove(key); false } else { set.add(key); true }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putStringSet(KEY, set).apply()
        return pinned
    }

    fun order(apps: List<AppEntry>, favs: Set<String>): List<AppEntry> =
        apps.filter { it.key in favs } + apps.filter { it.key !in favs }

    fun pinned(apps: List<AppEntry>, favs: Set<String>): List<AppEntry> = apps.filter { it.key in favs }
}
