package com.disp06.retroterm

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.SystemClock
import kotlin.random.Random

/**
 * Data feeding the home screen. Battery and Wi-Fi RSSI are real;
 * COMM LINK frequency and the spectrum strip are atmospheric noise.
 */
class Telemetry(ctx: Context) {
    private val wifi = runCatching { ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }.getOrNull()
    private val rnd = Random(SystemClock.elapsedRealtime())

    var battery = -1; private set
    var charging = false; private set

    /** 0..8 segments */
    var signal = 6; private set
    var signalReal = false; private set

    var commTenths = 784; private set  // 78.4 MHz
    val spectrum = IntArray(32) { 4 + (it * 7) % 9 }

    private var ticks = 0

    fun onBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) battery = level * 100 / scale
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun tick() {
        if (ticks++ % 5 == 0) readWifi()
        if (!signalReal) signal = (signal + rnd.nextInt(-1, 2)).coerceIn(4, 7)
        commTenths = 784 + rnd.nextInt(-3, 4)
        for (k in spectrum.indices) {
            val left = spectrum[if (k == 0) 0 else k - 1]
            spectrum[k] = ((spectrum[k] * 2 + left) / 3 + rnd.nextInt(-3, 4)).coerceIn(1, 12)
        }
    }

    @Suppress("DEPRECATION")
    private fun readWifi() {
        val info = runCatching { wifi?.connectionInfo }.getOrNull()
        if (info != null && info.networkId != -1 && info.rssi > -127) {
            signal = WifiManager.calculateSignalLevel(info.rssi, 9).coerceIn(0, 8)
            signalReal = true
        } else {
            signalReal = false
        }
    }
}
