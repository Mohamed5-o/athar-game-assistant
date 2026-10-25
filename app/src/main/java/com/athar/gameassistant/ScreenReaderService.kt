package com.athar.gameassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class ScreenReaderService : AccessibilityService() {
    companion object { const val ACTION_REVIEW = "com.athar.gameassistant.REVIEW" }
    private val main = Handler(Looper.getMainLooper())
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { takeScreenshotForReview() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter(ACTION_REVIEW)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
    }

    private fun takeScreenshotForReview() {
        if (Build.VERSION.SDK_INT < 30) {
            Toast.makeText(this, "يلزم Android 11 أو أحدث", Toast.LENGTH_LONG).show()
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, main, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val hardware = screenshot.hardwareBuffer
                val bitmap = Bitmap.wrapHardwareBuffer(hardware, screenshot.colorSpace)
                hardware.close()
                if (bitmap == null) {
                    broadcastResult("تعذّر التقاط الشاشة")
                    return
                }
                // OCR parsing intentionally fails closed until Arabic OCR is integrated and validated.
                bitmap.recycle()
                broadcastResult("⚠️ النسخة الأولية: قراءة العربية غير مفعّلة بعد. لا تعتمد على النتيجة.")
            }
            override fun onFailure(errorCode: Int) {
                broadcastResult("تعذّر التقاط الشاشة. تأكد من تفعيل خدمة إمكانية الوصول.")
            }
        })
    }

    private fun broadcastResult(message: String) {
        sendBroadcast(Intent("com.athar.gameassistant.RESULT").setPackage(packageName).putExtra("message", message))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}