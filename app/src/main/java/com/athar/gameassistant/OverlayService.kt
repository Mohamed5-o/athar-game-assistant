package com.athar.gameassistant

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class OverlayService : Service() {
    private var wm: WindowManager? = null
    private var root: LinearLayout? = null
    private var result: TextView? = null
    private var receiverRegistered = false

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            result?.text = intent?.getStringExtra(ScreenReaderService.EXTRA_MESSAGE) ?: "لم تصل أي نتيجة"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (root != null) return START_STICKY
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "اسمح أولاً بالظهور فوق التطبيقات", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(25, 34, 31))
        }
        val header = TextView(this).apply {
            text = "Athar • فحص الموظف"
            textSize = 15f
            setTextColor(Color.WHITE)
        }
        val resultView = TextView(this).apply {
            text = "اضغط مراجعة البيانات"
            textSize = 17f
            maxWidth = dp(260)
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(8))
        }
        val review = Button(this).apply {
            text = "مراجعة البيانات"
            setOnClickListener {
                if (!ScreenReaderService.isRunning) {
                    resultView.text = "فعّل خدمة إمكانية الوصول أولاً من التطبيق."
                    return@setOnClickListener
                }
                resultView.text = "جارٍ طلب لقطة الشاشة…"
                sendBroadcast(Intent(ScreenReaderService.ACTION_REVIEW).setPackage(packageName))
            }
        }
        val close = Button(this).apply {
            text = "إخفاء"
            setOnClickListener { stopSelf() }
        }
        panel.addView(header)
        panel.addView(resultView)
        panel.addView(review)
        panel.addView(close)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(90)
        }

        try {
            windowManager.addView(panel, params)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر عرض النافذة العائمة", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        wm = windowManager
        root = panel
        result = resultView

        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                resultReceiver,
                IntentFilter(ScreenReaderService.ACTION_RESULT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(resultReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        root?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        root = null
        result = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
