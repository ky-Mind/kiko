package com.devhunts.remotehp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * Mengumpulkan status perangkat (baterai, lokasi, info) untuk dashboard.
 * Semua opsional & aman: kalau izin lokasi belum ada, lokasi dikirim null.
 */
class StatusProvider(private val ctx: Context) {

    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Volatile
    private var lastLoc: Location? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) { lastLoc = location }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun hasLocPerm(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!hasLocPerm()) return
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (p in providers) {
                if (lm.isProviderEnabled(p)) {
                    lm.getLastKnownLocation(p)?.let { fresh ->
                        if (lastLoc == null || fresh.time > (lastLoc?.time ?: 0)) lastLoc = fresh
                    }
                    lm.requestLocationUpdates(p, 15_000L, 10f, listener, Looper.getMainLooper())
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun stop() {
        try { lm.removeUpdates(listener) } catch (_: Exception) {}
    }

    fun json(): String {
        val o = JSONObject()
        o.put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        o.put("android", Build.VERSION.RELEASE)
        o.put("locPerm", hasLocPerm())

        // Baterai
        try {
            val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (bi != null) {
                val level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
                val st = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                    st == BatteryManager.BATTERY_STATUS_FULL
                o.put("battery", pct)
                o.put("charging", charging)
            }
        } catch (_: Exception) {
            o.put("battery", -1)
        }

        // Lokasi
        val loc = lastLoc
        if (loc != null) {
            val l = JSONObject()
            l.put("lat", loc.latitude)
            l.put("lon", loc.longitude)
            l.put("acc", loc.accuracy.toDouble())
            l.put("time", loc.time)
            o.put("location", l)
        } else {
            o.put("location", JSONObject.NULL)
        }
        return o.toString()
    }
}
