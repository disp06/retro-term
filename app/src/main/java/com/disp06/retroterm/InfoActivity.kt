package com.disp06.retroterm

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.disp06.retroterm.ui.CrtOverlayView
import com.disp06.retroterm.ui.InfoPageView
import com.disp06.retroterm.ui.InfoState
import com.disp06.retroterm.ui.Palette

/**
 * INFO screen (tap the middle of the watch face): scroll up/down between
 * WEATHER (tap = refresh), BLUETOOTH (tap key = BT settings) and QUICK START (pinned apps).
 * Swipe right = back to the watch face.
 */
class InfoActivity : Activity() {

    private lateinit var palette: Palette
    private lateinit var pages: RecyclerView
    private val views = arrayOfNulls<InfoPageView>(3)
    private var state = InfoState()

    private val refresh: () -> Unit = { push() }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { push() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (ThemePrefs.isNight(ThemePrefs.mode(this))) R.style.Theme_RetroTerm_Night else R.style.Theme_RetroTerm_Day)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)
        palette = Palette.fromTheme(this)
        findViewById<CrtOverlayView>(R.id.crt).palette = palette

        pages = findViewById(R.id.pages)
        pages.layoutManager = LinearLayoutManager(this)
        pages.setHasFixedSize(true)
        pages.itemAnimator = null
        pages.adapter = Pages()
        PagerSnapHelper().attachToRecyclerView(pages)
        pages.scrollToPosition(intent.getIntExtra(EXTRA_PAGE, 0).coerceIn(0, 2))

        if (AppRepository.apps.isEmpty()) AppRepository.reload(this)
        if (!WeatherRepo.hasPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOC)
        } else {
            WeatherRepo.refreshIfStale(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        // with or without permission: without it the location comes from the IP address
        if (requestCode == REQ_LOC) WeatherRepo.refresh(this)
    }

    override fun onResume() {
        super.onResume()
        WeatherRepo.addListener(refresh)
        AppRepository.addListener(refresh)
        registerReceiver(btReceiver, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
        push()
    }

    override fun onPause() {
        WeatherRepo.removeListener(refresh)
        AppRepository.removeListener(refresh)
        runCatching { unregisterReceiver(btReceiver) }
        super.onPause()
    }

    private fun push() {
        state = InfoState(
            weather = WeatherRepo.cached(this),
            weatherBusy = WeatherRepo.busy,
            weatherError = WeatherRepo.lastError,
            bt = BtInfo.read(),
            favorites = Favorites.pinned(AppRepository.apps, Favorites.get(this)),
        )
        views.forEach { it?.state = state }
    }

    private fun onTap(kind: Int, idx: Int) {
        when (kind) {
            0 -> WeatherRepo.refresh(this)
            1 -> if (idx == InfoPageView.KEY) runCatching {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            2 -> when {
                idx == InfoPageView.KEY -> startActivity(Intent(this, AppListActivity::class.java))
                idx >= 0 -> state.favorites.getOrNull(idx)?.let { AppListActivity.launchApp(this, it) }
            }
        }
    }

    private inner class Pages : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 3
        override fun getItemViewType(position: Int) = position
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = InfoPageView(parent.context, viewType, ::onTap)
            v.palette = palette
            v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            views[viewType] = v
            return object : RecyclerView.ViewHolder(v) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder.itemView as InfoPageView).state = state
        }
    }

    companion object {
        const val EXTRA_PAGE = "page"
        private const val REQ_LOC = 7
    }
}
