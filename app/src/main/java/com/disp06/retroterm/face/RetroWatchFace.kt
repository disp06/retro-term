package com.disp06.retroterm.face

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.SurfaceHolder
import com.disp06.retroterm.AppListActivity
import com.disp06.retroterm.InfoActivity
import com.disp06.retroterm.Telemetry
import com.disp06.retroterm.ThemePrefs
import com.disp06.retroterm.WeatherRepo
import com.disp06.retroterm.ui.Palette
import com.disp06.retroterm.ui.TerminalRenderer

/**
 * RETRO-TERM as a watch face. Running inside the stock Wear OS home means every system gesture
 * keeps working: swipe left = tiles, up = notifications, down = quick settings.
 * Taps: header = theme, middle = INFO screen, APPS key = app index.
 */
class RetroWatchFace : CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine = RetroEngine()

    private inner class RetroEngine : CanvasWatchFaceService.Engine() {
        private lateinit var r: TerminalRenderer
        private lateinit var telemetry: Telemetry
        private val handler = Handler(Looper.getMainLooper())
        private var registered = false
        private var ticks = 0
        private val bounds = Rect()

        private val tick = object : Runnable {
            override fun run() {
                if (isVisible && !isInAmbientMode) {
                    telemetry.tick()
                    if (++ticks % 60 == 0) WeatherRepo.refreshIfStale(this@RetroWatchFace)
                    invalidate()
                    handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
                }
            }
        }

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_BATTERY_CHANGED) telemetry.onBattery(i)
                invalidate()
            }
        }

        private val weatherListener: () -> Unit = { invalidate() }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@RetroWatchFace)
                    .setAcceptsTapEvents(true)
                    .setShowUnreadCountIndicator(false)
                    .setHideNotificationIndicator(true) // unread count is shown in the STATUS row instead
                    .setStatusBarGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                    .build()
            )
            r = TerminalRenderer(this@RetroWatchFace)
            telemetry = Telemetry(this@RetroWatchFace)
            r.telemetry = telemetry
            WeatherRepo.addListener(weatherListener)
            applyTheme()
        }

        override fun onDestroy() {
            handler.removeCallbacks(tick)
            WeatherRepo.removeListener(weatherListener)
            unregister()
            super.onDestroy()
        }

        private fun applyTheme() {
            val mode = ThemePrefs.mode(this@RetroWatchFace)
            r.palette = Palette.forNight(ThemePrefs.isNight(mode))
            r.modeTag = mode.tag
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            r.lowBit = properties.getBoolean(WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            r.burnIn = properties.getBoolean(WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            r.ambient = inAmbientMode
            restartTicker()
            invalidate()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                register()
                r.is24 = DateFormat.is24HourFormat(this@RetroWatchFace)
                applyTheme()
                WeatherRepo.refreshIfStale(this@RetroWatchFace)
            } else {
                unregister()
            }
            restartTicker()
        }

        override fun onUnreadCountChanged(count: Int) {
            super.onUnreadCountChanged(count)
            invalidate()
        }

        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            if (tapType != WatchFaceService.TAP_TYPE_TAP) return
            when (r.hit(x.toFloat(), y.toFloat(), bounds.width(), bounds.height())) {
                TerminalRenderer.Zone.THEME -> { buzz(); ThemePrefs.cycle(this@RetroWatchFace); applyTheme(); invalidate() }
                TerminalRenderer.Zone.INFO -> { buzz(); open(InfoActivity::class.java) }
                TerminalRenderer.Zone.APPS -> { buzz(); open(AppListActivity::class.java) }
                TerminalRenderer.Zone.NONE -> Unit
            }
        }

        override fun onDraw(canvas: Canvas, b: Rect) {
            bounds.set(b)
            applyTheme() // cheap (prefs are cached); keeps AUTO switching on time
            r.setTime(System.currentTimeMillis())
            r.unread = unreadCount
            r.weather = WeatherRepo.cached(this@RetroWatchFace)
            r.draw(canvas, b.width(), b.height())
        }

        private fun open(cls: Class<*>) {
            startActivity(Intent(this@RetroWatchFace, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }

        private fun buzz() {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            runCatching { v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)) }
        }

        private fun restartTicker() {
            handler.removeCallbacks(tick)
            if (isVisible && !isInAmbientMode) handler.post(tick)
        }

        private fun register() {
            if (registered) return
            registered = true
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            registerReceiver(receiver, f)?.let { if (it.action == Intent.ACTION_BATTERY_CHANGED) telemetry.onBattery(it) }
        }

        private fun unregister() {
            if (!registered) return
            registered = false
            runCatching { unregisterReceiver(receiver) }
        }
    }
}
