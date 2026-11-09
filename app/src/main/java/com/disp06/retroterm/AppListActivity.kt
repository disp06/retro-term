package com.disp06.retroterm

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableRecyclerView
import com.disp06.retroterm.ui.AppAdapter
import com.disp06.retroterm.ui.CrtOverlayView
import com.disp06.retroterm.ui.DrawerChromeView
import com.disp06.retroterm.ui.Palette
import com.disp06.retroterm.ui.Ui

/**
 * [ APP INDEX ] — terminal-style list of every launchable app.
 * Tap = launch. Long-press = pin / unpin (pinned apps sit on top with a star).
 * Swipe right = back to the watch face.
 */
class AppListActivity : Activity() {

    private lateinit var palette: Palette
    private lateinit var list: WearableRecyclerView
    private lateinit var counter: TextView
    private lateinit var adapter: AppAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var lastSel = -2
    private var flashUntil = 0L

    private val appsListener: () -> Unit = { submit() }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { AppRepository.reload(c, i.data?.schemeSpecificPart) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (ThemePrefs.isNight(ThemePrefs.mode(this))) R.style.Theme_RetroTerm_Night else R.style.Theme_RetroTerm_Day)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        palette = Palette.fromTheme(this)

        list = findViewById(R.id.app_list)
        counter = findViewById(R.id.drawer_counter)
        findViewById<DrawerChromeView>(R.id.drawer_chrome).palette = palette
        findViewById<CrtOverlayView>(R.id.crt).palette = palette

        val s = Ui.scale(this)
        val title = findViewById<TextView>(R.id.drawer_title)
        styleText(title, s)
        (title.layoutParams as FrameLayout.LayoutParams).topMargin = (52 * s).toInt()
        styleText(counter, s)
        (counter.layoutParams as FrameLayout.LayoutParams).apply { height = (28 * s).toInt(); bottomMargin = (50 * s).toInt() }

        adapter = AppAdapter(palette, s, ::launch, ::toggleFavorite)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.itemAnimator = null
        list.setHasFixedSize(true)
        list.isEdgeItemsCenteringEnabled = true
        LinearSnapHelper().attachToRecyclerView(list)
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                for (i in 0 until rv.childCount) rv.getChildAt(i).invalidate()
                updateCounter()
            }
        })

        AppRepository.addListener(appsListener)
        if (AppRepository.apps.isEmpty()) AppRepository.reload(this) else submit()

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
    }

    override fun onResume() {
        super.onResume()
        // theme may have been changed on the watch face meanwhile
        if (ThemePrefs.isNight(ThemePrefs.mode(this)) != palette.night) recreate()
    }

    override fun onDestroy() {
        AppRepository.removeListener(appsListener)
        runCatching { unregisterReceiver(packageReceiver) }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun styleText(tv: TextView, s: Float) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.T_BODY * s)
        tv.setTextColor(palette.fg)
        if (palette.night) tv.setShadowLayer(7f * s, 0f, 0f, palette.glow)
    }

    private fun submit() {
        val favs = Favorites.get(this)
        adapter.submit(Favorites.order(AppRepository.apps, favs), favs)
        lastSel = -2
        list.post { updateCounter() }
    }

    private fun toggleFavorite(e: AppEntry) {
        val pinned = Favorites.toggle(this, e.key)
        submit()
        // keep the toggled app under the selector at its new position
        val idx = adapter.indexOf(e.key)
        if (idx >= 0) (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(idx, 0)
        flash(if (pinned) "* PINNED *" else "UNPINNED")
    }

    private fun flash(msg: String) {
        counter.text = msg
        flashUntil = System.currentTimeMillis() + 1200
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ flashUntil = 0; lastSel = -2; updateCounter() }, 1250)
    }

    private fun updateCounter() {
        if (System.currentTimeMillis() < flashUntil) return
        val total = adapter.itemCount
        val v = list.findChildViewUnder(list.width / 2f, list.height / 2f)
        val pos = if (v != null) list.getChildAdapterPosition(v) else -1
        if (pos == lastSel) return
        lastSel = pos
        counter.text = "${pad3(if (pos >= 0) pos + 1 else 0)}/${pad3(total)}"
    }

    private fun pad3(n: Int) = n.toString().padStart(3, '0')

    private fun launch(e: AppEntry) = launchApp(this, e)

    companion object {
        fun launchApp(ctx: Context, e: AppEntry) {
            val i = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            try {
                ctx.startActivity(i)
            } catch (ex: ActivityNotFoundException) {
                Toast.makeText(ctx, R.string.launch_failed, Toast.LENGTH_SHORT).show()
                AppRepository.reload(ctx, e.component.packageName)
            } catch (ex: SecurityException) {
                Toast.makeText(ctx, R.string.launch_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
