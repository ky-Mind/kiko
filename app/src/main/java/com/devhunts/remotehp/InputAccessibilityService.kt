package com.devhunts.remotehp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class InputAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun size(): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    fun tapNorm(nx: Float, ny: Float) {
        val (w, h) = size()
        val path = Path().apply { moveTo(nx * w, ny * h) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun swipeNorm(nx1: Float, ny1: Float, nx2: Float, ny2: Float, ms: Long) {
        val (w, h) = size()
        val path = Path().apply {
            moveTo(nx1 * w, ny1 * h)
            lineTo(nx2 * w, ny2 * h)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, ms.coerceIn(20, 3000)))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun global(action: Int) {
        performGlobalAction(action)
    }

    fun lockScreen(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= 28)
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        else false
    }

    fun typeText(s: String) {
        val node: AccessibilityNodeInfo = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, s)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    companion object {
        @Volatile
        var instance: InputAccessibilityService? = null
    }
}
