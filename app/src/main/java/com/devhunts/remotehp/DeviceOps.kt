package com.devhunts.remotehp

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/** Screen time (usage), daftar aplikasi, dan perintah (kunci/buka/pesan). */
object DeviceOps {

    private fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    /** Pemakaian app 24 jam terakhir (butuh izin Usage access). */
    fun usage(ctx: Context): String {
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 24L * 60 * 60 * 1000
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: emptyList()
            val agg = HashMap<String, Long>()
            for (s in stats) if (s.totalTimeInForeground > 0)
                agg[s.packageName] = (agg[s.packageName] ?: 0L) + s.totalTimeInForeground
            val arr = JSONArray()
            agg.entries.sortedByDescending { it.value }.take(15).forEach { (pkg, ms) ->
                val o = JSONObject(); o.put("pkg", pkg); o.put("app", label(ctx, pkg)); o.put("ms", ms)
                arr.put(o)
            }
            arr.toString()
        } catch (_: Exception) { "[]" }
    }

    /** Daftar aplikasi yang bisa dibuka (untuk perintah "buka app"). */
    fun apps(ctx: Context): String {
        return try {
            val pm = ctx.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val ris = pm.queryIntentActivities(i, 0)
            val seen = HashSet<String>()
            val arr = JSONArray()
            ris.mapNotNull { it.activityInfo?.packageName }
                .filter { seen.add(it) }
                .map { it to label(ctx, it) }
                .sortedBy { it.second.lowercase() }
                .forEach { (pkg, lbl) ->
                    val o = JSONObject(); o.put("pkg", pkg); o.put("app", lbl); arr.put(o)
                }
            arr.toString()
        } catch (_: Exception) { "[]" }
    }

    fun cmd(ctx: Context, action: String?, params: Map<String, List<String>>): Boolean {
        return try {
            when (action) {
                "lock" -> InputAccessibilityService.instance?.lockScreen() ?: false
                "open" -> {
                    val pkg = params["pkg"]?.firstOrNull() ?: return false
                    val li = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
                    li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(li); true
                }
                "message" -> {
                    val text = params["text"]?.firstOrNull() ?: return false
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
                    }
                    true
                }
                else -> false
            }
        } catch (_: Exception) { false }
    }
}
