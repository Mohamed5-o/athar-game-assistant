package com.athar.gameassistant

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A line of dark text inside a document (pixel rectangle in the screenshot). */
class Row(val y0: Int, val y1: Int, val x0: Int, val x1: Int)

/** One document: its coloured header bar and the text rows below it. */
class Panel(val hx: Int, val hy: Int, val hw: Int, val hh: Int, val rows: List<Row>)

class Layout(
    val lum: IntArray,
    val w: Int,
    val h: Int,
    val card: Panel,
    val res: Panel,
    val co: Panel
) {
    /** Hash of all text-row pixels: changes when a new employee / documents appear. */
    fun contentHash(): Long {
        var hsh = 1469598103934665603L
        for (p in listOf(card, res, co)) {
            for (r in p.rows) {
                for (y in r.y0 until r.y1) {
                    val base = y * w
                    for (x in r.x0 until r.x1) {
                        hsh = (hsh xor lum[base + x].toLong()) * 1099511628211L
                    }
                }
            }
        }
        return hsh
    }
}

/** Ink coverage map of a crop: 0 = paper, 1 = full ink. */
class Ink(val a: FloatArray, val w: Int, val h: Int)

/**
 * The game is drawn on a canvas, so no text can be read from the page. Instead we work on the
 * screenshot: documents are found by the exact colour of their header bars, text rows by dark ink,
 * and the same text drawn in two documents is compared pixel-wise (same font and size), which
 * exposes even a single extra / missing dot.
 */
object VisionAnalyzer {
    const val DARK = 90

    /** Eroded-difference pixels (at 4x) above which two renderings of one text are different. */
    const val THRESHOLD = 30

    private val COL_CARD = intArrayOf(31, 90, 73)
    private val COL_RES = intArrayOf(62, 92, 118)
    private val COL_CO = intArrayOf(127, 79, 36)

    // ---------------------------------------------------------------- layout

    fun detect(px: IntArray, w: Int, h: Int): Layout? {
        if (w <= 0 || h <= 0 || px.size < w * h) return null
        val hc = findHeader(px, w, h, COL_CARD) ?: return null
        val hr = findHeader(px, w, h, COL_RES) ?: return null
        val ho = findHeader(px, w, h, COL_CO) ?: return null
        val gap = hr[1] - hc[1]
        if (gap < 40) return null

        val lum = IntArray(w * h)
        for (i in lum.indices) {
            val c = px[i]
            lum[i] = (299 * ((c shr 16) and 255) + 587 * ((c shr 8) and 255) + 114 * (c and 255)) / 1000
        }
        val card = Panel(hc[0], hc[1], hc[2], hc[3], findRows(lum, w, h, hc, hr[1] - 2))
        val res = Panel(hr[0], hr[1], hr[2], hr[3], findRows(lum, w, h, hr, min(h, hr[1] + (gap * 0.8).toInt())))
        val co = Panel(ho[0], ho[1], ho[2], ho[3], findRows(lum, w, h, ho, ho[1] + (gap * 0.75).toInt()))
        return Layout(lum, w, h, card, res, co)
    }

    private fun match(c: Int, k: IntArray): Boolean =
        abs(((c shr 16) and 255) - k[0]) <= 3 &&
            abs(((c shr 8) and 255) - k[1]) <= 3 &&
            abs((c and 255) - k[2]) <= 3

    /** Returns [x, y, width, height] of the header bar painted in colour [k]. */
    private fun findHeader(px: IntArray, w: Int, h: Int, k: IntArray): IntArray? {
        val cnt = IntArray(h)
        for (y in 0 until h) {
            var c = 0
            val b = y * w
            for (x in 0 until w) if (match(px[b + x], k)) c++
            cnt[y] = c
        }
        var bs = -1
        var be = -1
        var bestS = -1
        var bestE = -1
        fun closeBand() {
            val hh = be - bs + 1
            if (bs >= 0 && hh in 12..80 && (bestS < 0 || hh > bestE - bestS + 1)) {
                bestS = bs
                bestE = be
            }
        }
        for (y in 0 until h) {
            if (cnt[y] >= 40) {
                if (bs < 0) {
                    bs = y
                    be = y
                } else if (y - be <= 4) {
                    be = y
                } else {
                    closeBand()
                    bs = y
                    be = y
                }
            }
        }
        closeBand()
        if (bestS < 0) return null
        val hh = bestE - bestS + 1
        val colCnt = IntArray(w)
        for (y in bestS..bestE) {
            val b = y * w
            for (x in 0 until w) if (match(px[b + x], k)) colCnt[x]++
        }
        var xmin = -1
        var xmax = -1
        val need = hh * 0.4
        for (x in 0 until w) {
            if (colCnt[x] >= need) {
                if (xmin < 0) xmin = x
                xmax = x
            }
        }
        if (xmin < 0) return null
        return intArrayOf(xmin, bestS, xmax - xmin + 1, hh)
    }

    private fun findRows(lum: IntArray, w: Int, h: Int, hd: IntArray, ybotIn: Int): List<Row> {
        val x = hd[0]
        val hw = hd[2]
        val ytop = hd[1] + hd[3]
        val ybot = min(ybotIn, h)
        val xa = x + 4
        val xb = min(x + hw - 4, w)
        if (ybot <= ytop || xb <= xa) return emptyList()
        val n = ybot - ytop
        val prof = IntArray(n)
        for (i in 0 until n) {
            val b = (ytop + i) * w
            var c = 0
            for (xx in xa until xb) if (lum[b + xx] < DARK) c++
            prof[i] = c
        }
        val runs = ArrayList<IntArray>()
        var st = -1
        for (i in 0 until n) {
            if (prof[i] >= 2) {
                if (st < 0) st = i
            } else if (st >= 0) {
                runs.add(intArrayOf(st, i))
                st = -1
            }
        }
        if (st >= 0) runs.add(intArrayOf(st, n))
        val merged = ArrayList<IntArray>()
        for (r in runs) {
            if (merged.isNotEmpty() && r[0] - merged[merged.size - 1][1] <= 1) {
                merged[merged.size - 1][1] = r[1]
            } else {
                merged.add(r)
            }
        }
        val out = ArrayList<Row>()
        for (m in merged) {
            val a = m[0]
            val b = m[1]
            if (b - a < 4) continue
            var mn = Int.MAX_VALUE
            var mx = -1
            for (i in a until b) {
                val base = (ytop + i) * w
                for (xx in xa until xb) {
                    if (lum[base + xx] < DARK) {
                        if (xx < mn) mn = xx
                        if (xx > mx) mx = xx
                    }
                }
            }
            if (mx < 0) continue
            val x0r = mn
            val x1r = mx + 1
            // frame lines and box titles are wide / reach the panel border: not value rows
            if ((x1r - x0r) > 0.8 * hw || x1r > x + hw - 14) continue
            out.add(Row(ytop + a, ytop + b, x0r, x1r))
        }
        return out
    }

    // ---------------------------------------------------------------- pixels

    fun crop(lum: IntArray, w: Int, y0: Int, y1: Int, x0: Int, x1: Int): Ink {
        val iw = x1 - x0
        val ih = y1 - y0
        val a = FloatArray(iw * ih)
        for (yy in 0 until ih) {
            for (xx in 0 until iw) {
                val v = lum[(y0 + yy) * w + x0 + xx]
                a[yy * iw + xx] = ((200f - v) / 155f).coerceIn(0f, 1f)
            }
        }
        return Ink(a, iw, ih)
    }

    /** [y0, y1) shrunk to the rows that really contain dark ink inside the x-range, or null. */
    fun tightY(lum: IntArray, w: Int, y0: Int, y1: Int, x0: Int, x1: Int): IntArray? {
        var a = -1
        var b = -1
        for (y in y0 until y1) {
            var any = false
            for (x in x0 until x1) if (lum[y * w + x] < DARK) { any = true; break }
            if (any) {
                if (a < 0) a = y
                b = y
            }
        }
        if (a < 0) return null
        return intArrayOf(a, b + 1)
    }

    private fun upsample(src: FloatArray, h: Int, w: Int, s: Int): FloatArray {
        val oh = h * s
        val ow = w * s
        val out = FloatArray(oh * ow)
        val y0s = IntArray(oh)
        val y1s = IntArray(oh)
        val fys = FloatArray(oh)
        for (o in 0 until oh) {
            val p = (o + 0.5f) / s - 0.5f
            val y0 = floor(p.toDouble()).toInt().coerceIn(0, h - 1)
            y0s[o] = y0
            y1s[o] = min(y0 + 1, h - 1)
            fys[o] = (p - y0).coerceIn(0f, 1f)
        }
        val x0s = IntArray(ow)
        val x1s = IntArray(ow)
        val fxs = FloatArray(ow)
        for (o in 0 until ow) {
            val p = (o + 0.5f) / s - 0.5f
            val x0 = floor(p.toDouble()).toInt().coerceIn(0, w - 1)
            x0s[o] = x0
            x1s[o] = min(x0 + 1, w - 1)
            fxs[o] = (p - x0).coerceIn(0f, 1f)
        }
        for (oy in 0 until oh) {
            val r0 = y0s[oy] * w
            val r1 = y1s[oy] * w
            val fy = fys[oy]
            for (ox in 0 until ow) {
                val fx = fxs[ox]
                val top = src[r0 + x0s[ox]] * (1 - fx) + src[r0 + x1s[ox]] * fx
                val bot = src[r1 + x0s[ox]] * (1 - fx) + src[r1 + x1s[ox]] * fx
                out[oy * ow + ox] = top * (1 - fy) + bot * fy
            }
        }
        return out
    }

    private fun padded(i: Ink, hh: Int, ww: Int): FloatArray {
        val o = FloatArray(hh * ww)
        for (y in 0 until i.h) for (x in 0 until i.w) o[(y + 1) * ww + x + 1] = i.a[y * i.w + x]
        return o
    }

    /**
     * Size of the area where two renderings of the same text really differ (in 4x pixels).
     * The images are aligned to a quarter pixel first, so thin anti-aliasing noise disappears and
     * only solid blobs (a dot, a different letter shape) survive the erosion.
     * Returns 9999 when the sizes are too different to be the same text.
     */
    fun blobDiff(a: Ink, b: Ink): Int {
        if (abs(a.w - b.w) > 3 || abs(a.h - b.h) > 3) return 9999
        val s = 4
        val hh = max(a.h, b.h) + 2
        val ww = max(a.w, b.w) + 2
        val ua = upsample(padded(a, hh, ww), hh, ww, s)
        val ub = upsample(padded(b, hh, ww), hh, ww, s)
        val uh = hh * s
        val uw = ww * s
        var bestSum = Float.MAX_VALUE
        var bestDy = 0
        var bestDx = 0
        for (dy in -6..6) {
            for (dx in -6..6) {
                var sum = 0f
                for (y in 0 until uh) {
                    val sy = ((y - dy) % uh + uh) % uh
                    val rowA = y * uw
                    val rowB = sy * uw
                    for (x in 0 until uw) {
                        val sx = ((x - dx) % uw + uw) % uw
                        sum += abs(ua[rowA + x] - ub[rowB + sx])
                    }
                }
                if (sum < bestSum) {
                    bestSum = sum
                    bestDy = dy
                    bestDx = dx
                }
            }
        }
        val d = BooleanArray(uh * uw)
        for (y in 0 until uh) {
            val sy = ((y - bestDy) % uh + uh) % uh
            for (x in 0 until uw) {
                val sx = ((x - bestDx) % uw + uw) % uw
                d[y * uw + x] = abs(ua[y * uw + x] - ub[sy * uw + sx]) > 0.5f
            }
        }
        var count = 0
        for (y in 1 until uh - 1) {
            for (x in 1 until uw - 1) {
                var all = true
                loop@ for (yy in -1..1) {
                    for (xx in -1..1) {
                        if (!d[(y + yy) * uw + x + xx]) {
                            all = false
                            break@loop
                        }
                    }
                }
                if (all) count++
            }
        }
        return count
    }

    // ---------------------------------------------------------------- checks

    private fun hidden(r: Row, blocked: IntArray?): Boolean =
        blocked != null && r.x0 < blocked[2] + 2 && r.x1 > blocked[0] - 2 &&
            r.y0 < blocked[3] + 2 && r.y1 > blocked[1] - 2

    private const val BLOCKED_MSG = "المربع العائم فوق الملف — حرّكه بعيد عن الملفات"

    private fun pairLine(a: String, b: String, score: Int): String =
        if (score >= 9999) "$a ≠ $b (طول مختلف)" else "$a ≠ $b (درجة الفرق $score)"

    /** Name written in the card, the company file and the residence proof. */
    fun nameCheck(l: Layout, blocked: IntArray?): CheckItem {
        val names = listOf("البطاقة", "الشركة", "السكن")
        val rows = listOf(l.card.rows.getOrNull(0), l.co.rows.getOrNull(0), l.res.rows.getOrNull(0))
        val missing = names.filterIndexed { i, _ -> rows[i] == null }
        if (missing.isNotEmpty()) {
            return CheckItem("الاسم", Status.UNKNOWN, listOf("مش لاقي سطر الاسم في: " + missing.joinToString("، ")))
        }
        if (rows.any { hidden(it!!, blocked) }) return CheckItem("الاسم", Status.UNKNOWN, listOf(BLOCKED_MSG))
        val inks = rows.map { crop(l.lum, l.w, it!!.y0, it.y1, it.x0, it.x1) }
        val bad = ArrayList<String>()
        for ((i, j) in listOf(0 to 1, 0 to 2, 1 to 2)) {
            val sc = blobDiff(inks[i], inks[j])
            if (sc > THRESHOLD) bad.add(pairLine(names[i], names[j], sc))
        }
        return if (bad.isEmpty()) CheckItem("الاسم", Status.OK) else CheckItem("الاسم", Status.BAD, bad)
    }

    /** Job: card vs company file. The company file adds a code like (J02) after the job: ignored. */
    fun jobCheck(l: Layout, blocked: IntArray?): CheckItem {
        val c = l.card.rows.getOrNull(1)
        val k = l.co.rows.getOrNull(1)
        if (c == null || k == null) return CheckItem("الوظيفة", Status.UNKNOWN, listOf("مش لاقي سطر الوظيفة"))
        if (hidden(c, blocked) || hidden(k, blocked)) return CheckItem("الوظيفة", Status.UNKNOWN, listOf(BLOCKED_MSG))
        val wC = c.x1 - c.x0
        val xs = k.x1 - wC
        if (xs < k.x0 - 3) return CheckItem("الوظيفة", Status.BAD, listOf("الوظيفة في الشركة أقصر من البطاقة"))
        val x0 = max(xs, 0)
        val ty = tightY(l.lum, l.w, k.y0, k.y1, x0, k.x1)
            ?: return CheckItem("الوظيفة", Status.UNKNOWN, listOf("مش قادر أقرا الوظيفة في الشركة"))
        val a = crop(l.lum, l.w, c.y0, c.y1, c.x0, c.x1)
        val b = crop(l.lum, l.w, ty[0], ty[1], x0, k.x1)
        val sc = blobDiff(a, b)
        return if (sc > THRESHOLD) CheckItem("الوظيفة", Status.BAD, listOf(pairLine("البطاقة", "الشركة", sc)))
        else CheckItem("الوظيفة", Status.OK)
    }

    /** Gender: card vs the employee record inside the company file. */
    fun genderCheck(l: Layout, blocked: IntArray?): CheckItem {
        val c = l.card.rows.getOrNull(4)
        val k = if (l.co.rows.size >= 3) l.co.rows[l.co.rows.size - 2] else null
        if (c == null || k == null) return CheckItem("الجنس", Status.UNKNOWN, listOf("مش لاقي سطر الجنس"))
        if (hidden(c, blocked) || hidden(k, blocked)) return CheckItem("الجنس", Status.UNKNOWN, listOf(BLOCKED_MSG))
        val sc = blobDiff(crop(l.lum, l.w, c.y0, c.y1, c.x0, c.x1), crop(l.lum, l.w, k.y0, k.y1, k.x0, k.x1))
        return if (sc > THRESHOLD) CheckItem("الجنس", Status.BAD, listOf(pairLine("البطاقة", "الشركة", sc)))
        else CheckItem("الجنس", Status.OK)
    }

    /** Birth-date rows (need OCR): card row 2 and the company record's first row. */
    fun cardDobRow(l: Layout): Row? = l.card.rows.getOrNull(2)

    fun companyDobRow(l: Layout): Row? =
        if (l.co.rows.size >= 3) l.co.rows[l.co.rows.size - 3] else null

    fun isHidden(r: Row, blocked: IntArray?): Boolean = hidden(r, blocked)
}
