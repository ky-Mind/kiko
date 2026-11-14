package com.devhunts.remotehp

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedDeque

/** Penyimpanan notifikasi terakhir (shared, dibaca web server). */
object NotifStore {
    private data class N(val pkg: String, val app: String, val title: String, val text: String, val time: Long)
    private val list = ConcurrentLinkedDeque<N>()
    private const val MAX = 60

    fun add(pkg: String, app: String, title: String, text: String, time: Long) {
        list.addFirst(N(pkg, app, title, text, time))
        while (list.size > MAX) list.pollLast()
    }

    fun json(): String {
        val arr = JSONArray()
        for (n in list) {
            val o = JSONObject()
            o.put("pkg", n.pkg); o.put("app", n.app)
            o.put("title", n.title); o.put("text", n.text); o.put("time", n.time)
            arr.put(o)
        }
        return arr.toString()
    }
}

/** Menangkap notifikasi masuk. User harus aktifkan "Akses notifikasi" sekali. */
class NotifListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            val n = sbn?.notification ?: return
            if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return // skip notif persisten
            val ex = n.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            if (title.isBlank() && text.isBlank()) return
            NotifStore.add(sbn.packageName, appLabel(sbn.packageName), title, text, sbn.postTime)
        } catch (_: Exception) {
        }
    }

    private fun appLabel(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }
}
