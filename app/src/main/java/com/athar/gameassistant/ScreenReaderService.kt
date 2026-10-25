package com.athar.gameassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

class ScreenReaderService : AccessibilityService() {
    companion object {
        const val ACTION_REVIEW = "com.athar.gameassistant.REVIEW"
        const val ACTION_RESULT = "com.athar.gameassistant.RESULT"
        const val EXTRA_MESSAGE = "message"

        /** True while the accessibility service is connected (same process as the overlay). */
        @Volatile
        var isRunning = false
            private set
    }

    private var receiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            takeScreenshotForReview()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this, receiver, IntentFilter(ACTION_REVIEW), ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
    }

    private fun takeScreenshotForReview() {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val buffer = screenshot.hardwareBuffer
                try {
                    val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                    if (bitmap == null) {
                        broadcastResult("تعذّر التقاط الشاشة")
                    } else {
                        // OCR parsing intentionally fails closed until Arabic OCR is integrated and validated.
                        bitmap.recycle()
                        broadcastResult("⚠️ النسخة الأولية: قراءة العربية غير مفعّلة بعد. لا تعتمد على النتيجة.")
                    }
                } finally {
                    buffer.close()
                }
            }

            override fun onFailure(errorCode: Int) {
                val message = if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                    "انتظر ثانية ثم اضغط مرة أخرى."
                } else {
                    "تعذّر التقاط الشاشة. تأكد من تفعيل خدمة إمكانية الوصول."
                }
                broadcastResult(message)
            }
        })
    }

    private fun broadcastResult(message: String) {
        sendBroadcast(
            Intent(ACTION_RESULT).setPackage(packageName).putExtra(EXTRA_MESSAGE, message)
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        isRunning = false
        if (receiverRegistered) {
            try { unregisterReceiver(receiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        super.onDestroy()
    }
}
