package com.athar.gameassistant

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Reads the TEXT of the game screen from the accessibility tree (exact characters, so dot
 * differences in names are visible) and re-checks automatically whenever the screen changes.
 * It never clicks anything and has no internet permission.
 */
class ScreenReaderService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: ScreenReaderService? = null
            private set

        /** Set by the overlay while it is shown; the service does no work when false. */
        @Volatile
        var overlayActive = false

        @Volatile
        var reportListener: ((Report) -> Unit)? = null

        val isRunning: Boolean get() = instance != null

        fun requestCheck() {
            instance?.scheduleCheck(0L, true)
        }

        fun requestDump() {
            instance?.dump()
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var forceNext = false
    private var pending = false
    private var lastSig: String? = null // only touched on the worker thread

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !overlayActive) return
        if (event.packageName?.toString() == packageName) return // ignore our own overlay
        scheduleCheck(600L, false)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun scheduleCheck(delayMs: Long, force: Boolean) {
        if (force) forceNext = true
        if (pending) return
        pending = true
        main.postDelayed({
            pending = false
            val f = forceNext
            forceNext = false
            runCheck(f)
        }, delayMs)
    }

    private fun runCheck(force: Boolean) {
        if (worker.isShutdown) return
        worker.execute {
            val report = try {
                analyze()
            } catch (e: Exception) {
                Report(emptyList(), null, "حصل خطأ أثناء القراءة: ${e.javaClass.simpleName}")
            }
            val sig = report.signature()
            if (force || sig != lastSig) {
                lastSig = sig
                main.post { reportListener?.invoke(report) }
            }
        }
    }

    private fun analyze(): Report {
        val root = findRoot()
            ?: return Report(emptyList(), null, "مش قادر أوصل لشاشة اللعبة. افتح اللعبة في المتصفح.")
        val tokens = collectTokens(root)
        if (tokens.isEmpty()) {
            return Report(
                emptyList(), null,
                "مفيش نصوص يقرأها النظام من الشاشة دي. اضغط «تشخيص» وابعت النتيجة."
            )
        }
        return Checker.run(DocParser.parse(tokens))
    }

    private fun findRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && active.packageName?.toString() != packageName) return active
        for (w in windows) {
            if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val r = w.root
                if (r != null && r.packageName?.toString() != packageName) return r
            }
        }
        return null
    }

    private fun collectTokens(root: AccessibilityNodeInfo): List<Token> {
        val out = ArrayList<Token>()
        collect(root, out, intArrayOf(6000), 0)
        return out
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<Token>, budget: IntArray, depth: Int) {
        if (budget[0] <= 0 || depth > 80) return
        budget[0]--
        if (node.packageName?.toString() == packageName) return
        val before = out.size
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, budget, depth + 1)
        }
        // Only leaf-level text is used: a parent that merely repeats its children is skipped.
        if (out.size == before) {
            val text = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            if (text != null) {
                val r = Rect()
                node.getBoundsInScreen(r)
                out.add(Token(text, r.left, r.top, r.right, r.bottom, node.isVisibleToUser))
            }
        }
    }

    /** Copies everything the service can read (plus what it parsed) to the clipboard. */
    private fun dump() {
        if (worker.isShutdown) return
        worker.execute {
            val sb = StringBuilder()
            try {
                val root = findRoot()
                if (root == null) {
                    sb.append("NO ROOT\n")
                } else {
                    sb.append("pkg=").append(root.packageName).append(" cls=").append(root.className).append('\n')
                    val tokens = collectTokens(root)
                    sb.append("tokens=").append(tokens.size).append('\n')
                    for ((i, t) in tokens.withIndex()) {
                        if (i >= 700) {
                            sb.append("...truncated\n")
                            break
                        }
                        val h = DocParser.headerType(t.text)
                        sb.append(i).append(" [")
                            .append(t.left).append(',').append(t.top).append(',')
                            .append(t.right).append(',').append(t.bottom)
                            .append("] v=").append(if (t.visible) 1 else 0)
                            .append(" | ").append(t.text.replace('\n', ' '))
                        if (h != null) sb.append("   <== HEADER ").append(h.name)
                        sb.append('\n')
                    }
                    sb.append("--- parsed ---\n")
                    for ((type, f) in DocParser.parse(tokens)) {
                        sb.append(type.name)
                            .append(": name=").append(f.name)
                            .append(" | address=").append(f.address)
                            .append(" | job=").append(f.job)
                            .append(" | dob=").append(f.dob)
                            .append(" | gender=").append(f.gender)
                            .append(" | age=").append(f.age)
                            .append('\n')
                    }
                }
            } catch (e: Exception) {
                sb.append("ERR ").append(e.toString())
            }
            val text = if (sb.length > 90000) sb.substring(0, 90000) else sb.toString()
            main.post {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("athar-diagnostic", text))
                Toast.makeText(this, "تم نسخ بيانات التشخيص — الصقها في الشات", Toast.LENGTH_LONG).show()
            }
        }
    }
}
