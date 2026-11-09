package com.disp06.retroterm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Current weather from Open-Meteo (free, no API key), fetched over the phone link.
 * Location: watch GPS / network fix if location permission is granted, else last saved fix,
 * else approximate IP location. Result is cached in prefs and shared with the watch face.
 */
object WeatherRepo {
    class Snapshot(
        val temp: Double, val code: Int, val wind: Double, val humidity: Int,
        val hi: Double, val lo: Double, val at: Long, val place: String
    ) {
        val short: String get() = cond(code).first
        val long: String get() = cond(code).second
        val icon: Array<String> get() = PixelArt.weatherIcon(code)
        fun tempStr(v: Double = temp): String { val t = Math.round(v).toInt(); return (if (t > 0) "+" else "") + t + "°C" }
    }

    private const val FILE = "retroterm"
    private const val K_SNAP = "wx_snap"
    private const val K_LAT = "wx_lat"
    private const val K_LON = "wx_lon"
    private const val K_PLACE = "wx_place"
    const val STALE_MS = 30 * 60 * 1000L

    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()

    @Volatile private var snap: Snapshot? = null
    @Volatile private var loaded = false
    @Volatile var busy = false; private set
    @Volatile var lastError: String? = null; private set
    @Volatile private var lastAttempt = 0L

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun cached(ctx: Context): Snapshot? {
        if (!loaded) {
            loaded = true
            snap = runCatching {
                ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(K_SNAP, null)?.let { decode(JSONObject(it)) }
            }.getOrNull()
        }
        return snap
    }

    /** Refresh if older than [STALE_MS]; retries at most every 5 min after a failure. */
    fun refreshIfStale(ctx: Context) {
        val s = cached(ctx)
        val now = System.currentTimeMillis()
        if ((s == null || now - s.at > STALE_MS) && now - lastAttempt > 5 * 60 * 1000L) refresh(ctx)
    }

    fun refresh(ctx: Context) {
        if (busy) return
        busy = true; lastAttempt = System.currentTimeMillis()
        notifyListeners()
        val app = ctx.applicationContext
        exec.execute {
            try {
                val (lat, lon, place) = locate(app)
                val url = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f" +
                        "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                        "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto&wind_speed_unit=ms",
                    lat, lon
                )
                val j = JSONObject(get(url))
                val cur = j.getJSONObject("current")
                val daily = j.getJSONObject("daily")
                val s = Snapshot(
                    cur.getDouble("temperature_2m"), cur.getInt("weather_code"), cur.getDouble("wind_speed_10m"),
                    cur.optInt("relative_humidity_2m", -1),
                    daily.getJSONArray("temperature_2m_max").getDouble(0), daily.getJSONArray("temperature_2m_min").getDouble(0),
                    System.currentTimeMillis(), place
                )
                snap = s; loaded = true; lastError = null
                app.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(K_SNAP, encode(s).toString()).apply()
            } catch (e: Exception) {
                lastError = when (e) {
                    is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.io.IOException -> "NO NETWORK"
                    is NoFixException -> "NO LOCATION FIX"
                    else -> "ERROR"
                }
            } finally {
                busy = false
                notifyListeners()
            }
        }
    }

    private fun notifyListeners() = main.post { listeners.toList().forEach { it() } }

    // ---------------------------------------------------------------- location

    private class NoFixException : Exception()

    private fun locate(ctx: Context): Triple<Double, Double, String> {
        val prefs = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val fix = if (hasPermission(ctx)) deviceFix(ctx) else null
        if (fix != null) {
            val place = String.format(Locale.US, "%.2f %.2f", fix.latitude, fix.longitude)
            prefs.edit().putFloat(K_LAT, fix.latitude.toFloat()).putFloat(K_LON, fix.longitude.toFloat())
                .putString(K_PLACE, place).apply()
            return Triple(fix.latitude, fix.longitude, place)
        }
        // approximate location from public IP
        runCatching {
            val j = JSONObject(get("https://ipwho.is/?fields=success,latitude,longitude,city"))
            if (j.optBoolean("success", false)) {
                val lat = j.getDouble("latitude"); val lon = j.getDouble("longitude")
                val place = j.optString("city", "").uppercase(Locale.ROOT).ifEmpty { "IP LOC" }
                prefs.edit().putFloat(K_LAT, lat.toFloat()).putFloat(K_LON, lon.toFloat()).putString(K_PLACE, place).apply()
                return Triple(lat, lon, place)
            }
        }
        if (prefs.contains(K_LAT)) {
            return Triple(prefs.getFloat(K_LAT, 0f).toDouble(), prefs.getFloat(K_LON, 0f).toDouble(), prefs.getString(K_PLACE, "") ?: "")
        }
        throw NoFixException()
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun deviceFix(ctx: Context): Location? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 3600_000L) return last
        val provider = listOf(LocationManager.NETWORK_PROVIDER, "fused", LocationManager.GPS_PROVIDER).firstOrNull { it in providers }
            ?: return last
        val latch = CountDownLatch(1)
        var got: Location? = null
        val l = object : LocationListener {
            override fun onLocationChanged(loc: Location) { got = loc; latch.countDown() }
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }
        runCatching { lm.requestSingleUpdate(provider, l, Looper.getMainLooper()) }.onFailure { return last }
        latch.await(25, TimeUnit.SECONDS)
        runCatching { lm.removeUpdates(l) }
        return got ?: last
    }

    // ---------------------------------------------------------------- io

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "RetroTerm/2.0")
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun encode(s: Snapshot) = JSONObject()
        .put("t", s.temp).put("c", s.code).put("w", s.wind).put("h", s.humidity)
        .put("hi", s.hi).put("lo", s.lo).put("at", s.at).put("p", s.place)

    private fun decode(j: JSONObject) = Snapshot(
        j.getDouble("t"), j.getInt("c"), j.getDouble("w"), j.optInt("h", -1),
        j.getDouble("hi"), j.getDouble("lo"), j.getLong("at"), j.optString("p", "")
    )

    /** WMO weather code -> (3-letter tag, long name) */
    fun cond(code: Int): Pair<String, String> = when (code) {
        0 -> "CLR" to "CLEAR"
        1, 2 -> "CLD" to "PARTLY CLOUDY"
        3 -> "OVC" to "OVERCAST"
        45, 48 -> "FOG" to "FOG"
        51, 53, 55, 56, 57 -> "DRZ" to "DRIZZLE"
        61, 63, 65, 66, 67, 80, 81, 82 -> "RAN" to "RAIN"
        71, 73, 75, 77, 85, 86 -> "SNW" to "SNOW"
        95, 96, 99 -> "STM" to "THUNDERSTORM"
        else -> "???" to "UNKNOWN"
    }
}
