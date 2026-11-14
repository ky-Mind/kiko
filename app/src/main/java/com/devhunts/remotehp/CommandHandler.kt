package com.devhunts.remotehp

import android.accessibilityservice.AccessibilityService
import org.json.JSONObject

/**
 * Parses JSON control messages from the dashboard and routes them to the
 * accessibility service. Coordinates are normalized (0..1).
 */
object CommandHandler {

    fun handle(text: String?) {
        if (text.isNullOrBlank()) return
        val svc = InputAccessibilityService.instance ?: return
        val o = JSONObject(text)
        when (o.optString("type")) {
            "tap" -> svc.tapNorm(
                o.getDouble("x").toFloat(),
                o.getDouble("y").toFloat()
            )
            "swipe" -> svc.swipeNorm(
                o.getDouble("x1").toFloat(),
                o.getDouble("y1").toFloat(),
                o.getDouble("x2").toFloat(),
                o.getDouble("y2").toFloat(),
                o.optLong("ms", 200L)
            )
            "key" -> when (o.optString("action")) {
                "back" -> svc.global(AccessibilityService.GLOBAL_ACTION_BACK)
                "home" -> svc.global(AccessibilityService.GLOBAL_ACTION_HOME)
                "recents" -> svc.global(AccessibilityService.GLOBAL_ACTION_RECENTS)
                "notifications" -> svc.global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            }
            "text" -> svc.typeText(o.optString("value"))
        }
    }
}
