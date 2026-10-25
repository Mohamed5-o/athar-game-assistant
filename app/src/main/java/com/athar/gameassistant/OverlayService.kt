package com.athar.gameassistant

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var result: TextView? = null

    private val resultReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            result?.text = intent?.getStringExtra("message") ?: "לא התקבלה תוצאה"
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (root != null) return START_STICKY
        val resultFilter = android.content.IntentFilter("com.athar.gameassistant.RESULT")
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(resultReceiver, resultFilter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(resultReceiver, resultFilter)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 10, 14, 10)
            setBackgroundColor(Color.rgb(25, 34, 31))
        }
        val header = TextView(this).apply {
            text = "Athar • فحص الموظف"
            textSize = 15f
            setTextColor(Color.WHITE)
        }
        result = TextView(this).apply {
            text = "اضغط مراجعة البيانات"
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(0, 10, 0, 10)
        }
        val review = Button(this).apply {
            text = "مراجعة البيانات"
            setOnClickListener {
                result?.text = "جارٍ طلب لقطة الشاشة…"
                sendBroadcast(Intent(ScreenReaderService.ACTION_REVIEW).setPackage(packageName))
            }
        }
        val close = Button(this).apply {
            text = "إخفاء"
            setOnClickListener { stopSelf() }
        }
        panel.addView(header); panel.addView(result); panel.addView(review); panel.addView(close)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 16; y = 180 }
        wm.addView(panel, params)
        root = panel
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(resultReceiver) } catch (_: Exception) {}
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    fun showResult(text: String) { result?.text = text }
}