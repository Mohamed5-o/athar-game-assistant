package com.athar.gameassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * While the floating box is shown it takes a screenshot about every 1.5 s, finds the three
 * documents by their coloured headers and checks them (see [VisionAnalyzer]). It only looks at
 * the screen: it never clicks anything, stores nothing and has no internet permission.
 */
class ScreenReaderService : AccessibilityService() {
    companion object {
        private const val POLL_MS = 1500L

        @Volatile
        var instance: ScreenReaderService? = null
            private set

        @Volatile
        var overlayActive = false

        /** [left, top, right, bottom] of the floating box on screen (so it is not read as a document). */
        @Volatile
        var overlayRect: IntArray? = null

        @Volatile
        var reportListener: ((Report) -> Unit)? = null

        val isRunning: Boolean get() = instance != null

        fun requestCheck() {
            instance?.capture(true, false)
        }

        fun requestDump() {
            instance?.capture(true, true)
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // worker-thread only
    private var lastSig: String? = null
    private var analyzedKey = 0L
    private var hasAnalyzed = false
    private var pendingKey = 0L
    private var hasPending = false

    private val loop = object : Runnable {
        override fun run() {
            if (overlayActive) capture(false, false)
            main.postDelayed(this, POLL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        main.removeCallbacks(loop)
        main.postDelayed(loop, POLL_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        try {
            recognizer.close()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------ capture

    private fun capture(force: Boolean, dump: Boolean) {
        if (worker.isShutdown) return
        if (!busy.compareAndSet(false, true)) {
            if (force) main.postDelayed({ capture(force, dump) }, 400L)
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                try {
                    handle(screenshot, force, dump)
                } catch (e: Exception) {
                    emit(Report.note("حصل خطأ أثناء التحليل: ${e.javaClass.simpleName}"), true)
                } finally {
                    busy.set(false)
                }
            }

            override fun onFailure(errorCode: Int) {
                busy.set(false)
                if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                    if (force) main.postDelayed({ capture(force, dump) }, 1100L)
                } else {
                    emit(Report.note("تعذّر التقاط الشاشة (كود $errorCode)."), true)
                }
            }
        })
    }

    private fun emit(report: Report, force: Boolean) {
        val sig = report.signature()
        if (force || sig != lastSig) {
            lastSig = sig
            main.post { reportListener?.invoke(report) }
        }
    }

    private fun handle(shot: ScreenshotResult, force: Boolean, dump: Boolean) {
        val buffer = shot.hardwareBuffer
        val bmp: Bitmap? = try {
            val hw = Bitmap.wrapHardwareBuffer(buffer, shot.colorSpace)
            val copy = hw?.copy(Bitmap.Config.ARGB_8888, false)
            hw?.recycle()
            copy
        } finally {
            buffer.close()
        }
        if (bmp == null) {
            emit(Report.note("تعذّر التقاط الشاشة."), force)
            return
        }
        try {
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            val lay = VisionAnalyzer.detect(px, w, h)
            if (lay == null) {
                hasAnalyzed = false
                hasPending = false
                emit(Report.note("مش لاقي ملفات الموظف (البطاقة وملف الشركة وإثبات السكن) على الشاشة."), force)
                if (dump) saveDebug(bmp, null, emptyList(), "no layout")
                return
            }
            val blocked = overlayRect
            val key = lay.contentHash() * 31L + (blocked?.contentHashCode() ?: 0)
            if (!force) {
                if (hasAnalyzed && key == analyzedKey) return
                if (!hasPending || key != pendingKey) {
                    // new / still-changing documents: wait until the screen is stable (two identical captures)
                    pendingKey = key
                    hasPending = true
                    emit(Report.note("⏳ جارٍ الفحص…"), false)
                    return
                }
            }
            analyzedKey = key
            hasAnalyzed = true
            pendingKey = key
            hasPending = true

            val items = ArrayList<CheckItem>()
            items.add(VisionAnalyzer.nameCheck(lay, blocked))
            items.add(VisionAnalyzer.govCheck(lay, blocked))
            val ocrLog = StringBuilder()
            val (dobItem, ageItem) = dobAndAge(lay, blocked, ocrLog)
            items.add(dobItem)
            items.add(ageItem)
            items.add(VisionAnalyzer.jobCheck(lay, blocked))
            items.add(VisionAnalyzer.genderCheck(lay, blocked))
            emit(Report.of(items), force)
            if (dump) saveDebug(bmp, lay, items, ocrLog.toString())
        } finally {
            bmp.recycle()
        }
    }

    // ------------------------------------------------------------ digits (OCR)

    private class Dob(val date: String?, val age: Int?, val raw: String)

    private val dateRe = Regex("(\\d{4})\\D{0,3}(\\d{1,2})\\D{0,3}(\\d{1,2})")
    private val numRe = Regex("\\d{1,3}")

    private fun parseDob(text: String?): Dob {
        val t = (text ?: "").replace('\n', ' ')
        val m = dateRe.find(t) ?: return Dob(null, null, t)
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        val valid = y in 1900..2100 && mo in 1..12 && d in 1..31
        // the small age number sits next to the date, before or after it depending on the text order
        val before = t.substring(0, m.range.first)
        val after = t.substring(m.range.last + 1)
        val age = numRe.findAll(before).lastOrNull()?.value?.toIntOrNull()
            ?: numRe.find(after)?.value?.toIntOrNull()
        return Dob(if (valid) "$y-$mo-$d" else null, age, t)
    }

    /** Two reads at different zoom levels must agree, otherwise the value is treated as unreadable. */
    private fun readDob(lay: Layout, row: Row): Dob {
        val a = parseDob(ocr(lay, row, 4))
        val b = parseDob(ocr(lay, row, 6))
        return Dob(
            if (a.date == b.date) a.date else null,
            if (a.age == b.age) a.age else null,
            a.raw.trim() + " | " + b.raw.trim()
        )
    }

    private fun ocr(lay: Layout, row: Row, scale: Int): String? {
        val pad = 4
        val x0 = max(0, row.x0 - pad)
        val x1 = min(lay.w, row.x1 + pad)
        val y0 = max(0, row.y0 - pad)
        val y1 = min(lay.h, row.y1 + pad)
        val w = x1 - x0
        val h = y1 - y0
        if (w <= 0 || h <= 0) return null
        val pix = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = lay.lum[(y0 + y) * lay.w + x0 + x]
                val g = ((v - 45) * 255 / 155).coerceIn(0, 255)
                pix[y * w + x] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
        val small = Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
        val big = Bitmap.createScaledBitmap(small, w * scale, h * scale, true)
        return try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(big, 0)), 5, TimeUnit.SECONDS).text
        } catch (e: Exception) {
            null
        } finally {
            small.recycle()
            big.recycle()
        }
    }

    private fun dobAndAge(lay: Layout, blocked: IntArray?, log: StringBuilder): Pair<CheckItem, CheckItem> {
        val cr = VisionAnalyzer.cardDobRow(lay)
        val kr = VisionAnalyzer.companyDobRow(lay)
        if (cr == null || kr == null) {
            val m = listOf("مش لاقي سطر تاريخ الميلاد")
            return CheckItem("تاريخ الميلاد", Status.UNKNOWN, m) to CheckItem("العمر", Status.UNKNOWN, m)
        }
        if (VisionAnalyzer.isHidden(cr, blocked) || VisionAnalyzer.isHidden(kr, blocked)) {
            val m = listOf("المربع العائم فوق الملف — حرّكه بعيد عن الملفات")
            return CheckItem("تاريخ الميلاد", Status.UNKNOWN, m) to CheckItem("العمر", Status.UNKNOWN, m)
        }
        val card = readDob(lay, cr)
        val comp = readDob(lay, kr)
        log.append("OCR card: ").append(card.raw).append('\n')
        log.append("OCR company: ").append(comp.raw).append('\n')

        val dobItem = when {
            card.date == null || comp.date == null -> CheckItem(
                "تاريخ الميلاد", Status.UNKNOWN,
                listOf(
                    "مش قادر أقرا الأرقام كويس.",
                    "البطاقة: «${card.raw.trim().take(26)}»",
                    "الشركة: «${comp.raw.trim().take(26)}»"
                )
            )
            card.date == comp.date -> CheckItem("تاريخ الميلاد", Status.OK)
            else -> CheckItem(
                "تاريخ الميلاد", Status.BAD,
                listOf("البطاقة: ${card.date}", "الشركة: ${comp.date}")
            )
        }
        val age = card.age
        val ageItem = when {
            age == null -> CheckItem("العمر", Status.UNKNOWN, listOf("مش قادر أقرا العمر من البطاقة."))
            age in 18..65 -> CheckItem("العمر ($age)", Status.OK)
            else -> CheckItem("العمر ($age)", Status.BAD, listOf("خارج الحد المسموح 18–65"))
        }
        return dobItem to ageItem
    }

    // ------------------------------------------------------------ diagnostics

    private fun saveDebug(bmp: Bitmap, lay: Layout?, items: List<CheckItem>, ocrLog: String) {
        val sb = StringBuilder()
        sb.append("screen=").append(bmp.width).append('x').append(bmp.height).append('\n')
        if (lay == null) {
            sb.append("layout: NOT FOUND\n")
        } else {
            for ((name, p) in listOf("card" to lay.card, "company" to lay.co, "residence" to lay.res)) {
                sb.append(name).append(" header=[").append(p.hx).append(',').append(p.hy).append(',')
                    .append(p.hw).append(',').append(p.hh).append("] rows=").append(p.rows.size).append('\n')
                for ((i, r) in p.rows.withIndex()) {
                    sb.append("  ").append(i).append(": y ").append(r.y0).append('-').append(r.y1)
                        .append("  x ").append(r.x0).append('-').append(r.x1).append('\n')
                }
            }
        }
        for (it in items) {
            sb.append(it.status).append(' ').append(it.title).append(' ').append(it.lines.joinToString(" | ")).append('\n')
        }
        sb.append(ocrLog)
        val stamp = System.currentTimeMillis()
        val saved = savePng(bmp, "athar_$stamp.png")
        val text = sb.toString()
        main.post {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("athar-diagnostic", text))
            Toast.makeText(
                this,
                if (saved) "تم نسخ التشخيص وحفظ لقطة الشاشة في Pictures/Athar" else "تم نسخ التشخيص (تعذّر حفظ اللقطة)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun savePng(bmp: Bitmap, name: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Athar")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
