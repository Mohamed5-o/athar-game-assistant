package com.athar.gameassistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = "Athar Game Assistant\n\n1) اسمح بالظهور فوق التطبيقات.\n2) فعّل خدمة إمكانية الوصول.\n3) افتح اللعبة ثم اضغط «مراجعة البيانات» في المربع العائم."
            textSize = 18f
        }
        val overlay = Button(this).apply {
            text = "السماح بالنافذة العائمة"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
        }
        val access = Button(this).apply {
            text = "فتح إعدادات إمكانية الوصول"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val start = Button(this).apply {
            text = "تشغيل المربع العائم"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startService(Intent(this@MainActivity, OverlayService::class.java))
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "اسمح أولاً بالظهور فوق التطبيقات",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        layout.addView(title)
        layout.addView(overlay)
        layout.addView(access)
        layout.addView(start)

        // targetSdk 35 forces edge-to-edge: keep content clear of the system bars.
        val root = ScrollView(this).apply {
            isFillViewport = true
            addView(layout)
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
                insets
            }
        }
        setContentView(root)
    }
}
