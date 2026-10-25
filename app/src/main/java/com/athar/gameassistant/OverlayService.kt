package com.athar.gameassistant

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.IBinder
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class OverlayService : Service() {
    private var wm: WindowManager? = null
    private var panel: LinearLayout? = null
    private var banner: TextView? = null
    private var details: TextView? = null

    private val green = Color.rgb(76, 175, 80)
    private val red = Color.rgb(255, 82, 82)
    private val amber = Color.rgb(255, 193, 7)
    private val gray = Color.rgb(170, 170, 170)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (panel != null) return START_STICKY
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "اسمح أولاً بالظهور فوق التطبيقات", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.argb(235, 20, 28, 25))
        }
        val header = TextView(this).apply {
            text = "Athar • فحص الموظف  ⠿ اسحب للتحريك"
            textSize = 11f
            setTextColor(Color.LTGRAY)
        }
        val bannerView = TextView(this).apply {
            text = "افتح ملف موظف أو اضغط فحص"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(4))
        }
        val detailsView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            maxWidth = dp(250)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(detailsView)
        }

        fun smallButton(label: String, onClick: () -> Unit): Button {
            val b = Button(this)
            b.text = label
            b.textSize = 12f
            b.minHeight = 0
            b.minimumHeight = 0
            b.minWidth = 0
            b.minimumWidth = 0
            b.setPadding(dp(10), dp(4), dp(10), dp(4))
            b.setOnClickListener { onClick() }
            return b
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        buttons.addView(smallButton("فحص") {
            if (!ScreenReaderService.isRunning) {
                showMessage("فعّل خدمة إمكانية الوصول أولاً من التطبيق.")
            } else {
                ScreenReaderService.requestCheck()
            }
        })
        buttons.addView(smallButton("تشخيص") {
            if (!ScreenReaderService.isRunning) {
                showMessage("فعّل خدمة إمكانية الوصول أولاً من التطبيق.")
            } else {
                ScreenReaderService.requestDump()
            }
        })
        buttons.addView(smallButton("−") {
            body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        buttons.addView(smallButton("✕") { stopSelf() })

        p.addView(header)
        p.addView(bannerView)
        p.addView(body)
        p.addView(buttons)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.LEFT
        lp.x = dp(8)
        lp.y = dp(8)

        // Drag the panel by its title / status line.
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        val drag = View.OnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = ev.rawX
                    touchY = ev.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (ev.rawX - touchX).toInt()
                    lp.y = startY + (ev.rawY - touchY).toInt()
                    try {
                        windowManager.updateViewLayout(p, lp)
                    } catch (_: Exception) {
                    }
                    true
                }
                else -> false
            }
        }
        header.setOnTouchListener(drag)
        bannerView.setOnTouchListener(drag)

        try {
            windowManager.addView(p, lp)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر عرض النافذة العائمة", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        wm = windowManager
        panel = p
        banner = bannerView
        details = detailsView

        ScreenReaderService.reportListener = { r -> render(r) }
        ScreenReaderService.overlayActive = true
        if (ScreenReaderService.isRunning) {
            ScreenReaderService.requestCheck()
        } else {
            showMessage("⚠️ فعّل خدمة إمكانية الوصول من التطبيق ثم اضغط فحص.")
        }
        return START_STICKY
    }

    private fun showMessage(msg: String) {
        banner?.text = msg
        banner?.setTextColor(amber)
        details?.text = ""
    }

    private fun SpannableStringBuilder.line(text: String, color: Int, size: Float = 1f) {
        val start = length
        append(text).append('\n')
        setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (size != 1f) setSpan(RelativeSizeSpan(size), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun render(r: Report) {
        val b = banner ?: return
        val d = details ?: return
        when (r.overall) {
            Status.OK -> {
                b.text = "✅ كل المطابقات سليمة"
                b.setTextColor(green)
            }
            Status.BAD -> {
                b.text = "❌ فيه غلط"
                b.setTextColor(red)
            }
            Status.UNKNOWN -> {
                b.text = "❓ مش قادر أقرا كل البيانات"
                b.setTextColor(amber)
            }
            else -> {
                b.text = r.note ?: ""
                b.setTextColor(amber)
            }
        }
        val sb = SpannableStringBuilder()
        for (item in r.items) {
            val (icon, color) = when (item.status) {
                Status.OK -> Pair("✅", green)
                Status.BAD -> Pair("❌", red)
                Status.UNKNOWN -> Pair("❓", amber)
                Status.NA -> Pair("➖", gray)
            }
            sb.line("$icon ${item.title}", color)
            for (l in item.lines) sb.line("     $l", color, 0.9f)
        }
        d.text = sb
    }

    override fun onDestroy() {
        ScreenReaderService.overlayActive = false
        ScreenReaderService.reportListener = null
        panel?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        panel = null
        banner = null
        details = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
