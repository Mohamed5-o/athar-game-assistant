package com.athar.gameassistant

import kotlin.math.abs

/**
 * Splits the screen text into the three documents (by their headers, in screen-tree order)
 * and extracts name / address / job / birth date / gender / age from each.
 */
object DocParser {
    private val HDR_CARD = listOf("بطاقة تحقيق الشخصية", "جمهورية مصر العربية").map { TextUtil.key(it) }
    private val HDR_RES = listOf("إثبات سكن", "فاتورة مرافق").map { TextUtil.key(it) }
    private val W_FILE = TextUtil.key("ملف")
    private val W_CO = TextUtil.key("شركة")
    private val BREAKS = listOf(
        "دفتر موظفي", "اضغط القاعدة", "اسحب الختم", "الذهاب للشباك", "جدول الشركة", "المحاسب حسب"
    ).map { TextUtil.key(it) }

    private val L_NAME = listOf("اسم المشترك", "الاسم").map { TextUtil.key(it) }
    private val L_ADDR = listOf("العنوان").map { TextUtil.key(it) }
    private val L_JOB = listOf("الوظيفة").map { TextUtil.key(it) }
    private val L_DOB = listOf("تاريخ الميلاد").map { TextUtil.key(it) }
    private val L_GENDER = listOf("نوع", "الجنس", "النوع").map { TextUtil.key(it) }
    private val L_AGE = listOf("العمر").map { TextUtil.key(it) }
    private val ALL_LABELS: Set<String> = (
        L_NAME + L_ADDR + L_JOB + L_DOB + L_GENDER + L_AGE +
            listOf(
                "محل الميلاد", "حالة الاشتراك", "الرقم القومي",
                "سجل الموظف", "سجل الوظيفة", "الحالة", "المرتب"
            ).map { TextUtil.key(it) }
        ).toSet()

    private val DATE_YMD = Regex("(\\d{4})\\s*[/\\-.]\\s*(\\d{1,2})\\s*[/\\-.]\\s*(\\d{1,2})")
    private val DATE_DMY = Regex("(\\d{1,2})\\s*[/\\-.]\\s*(\\d{1,2})\\s*[/\\-.]\\s*(\\d{4})")
    private val AGE_RE = Regex("(\\d{1,3})\\s*(?:سنة|سنه|سنوات)")
    private val CODE_PAREN = Regex("[(\\[{][^)\\]}]*[)\\]}]")
    private val CODE_BARE = Regex("\\b[A-Za-z]{1,3}-?\\d{1,4}\\b")

    fun headerType(raw: String): DocType? {
        val k = TextUtil.key(raw)
        if (k.isEmpty() || k.length > 80) return null
        if (HDR_CARD.any { k.contains(it) }) return DocType.CARD
        if (HDR_RES.any { k.contains(it) }) return DocType.RESIDENCE
        if (k.startsWith(W_FILE) && (k.length == W_FILE.length || k.contains(W_CO) || k.contains("•"))) {
            return DocType.COMPANY
        }
        return null
    }

    private fun isBreak(raw: String): Boolean {
        val k = TextUtil.key(raw)
        return BREAKS.any { k.contains(it) }
    }

    private fun isLabel(raw: String): Boolean = TextUtil.key(raw) in ALL_LABELS

    fun parse(tokens: List<Token>): Map<DocType, DocFields> {
        val panels = LinkedHashMap<DocType, MutableList<Token>>()
        var current: DocType? = null
        for (t in tokens) {
            val h = headerType(t.text)
            if (h != null) {
                current = h
                if (!panels.containsKey(h)) panels[h] = mutableListOf()
                continue
            }
            if (isBreak(t.text)) {
                current = null
                continue
            }
            val c = current
            if (c != null) panels[c]?.add(t)
        }
        val out = LinkedHashMap<DocType, DocFields>()
        for ((type, toks) in panels) out[type] = parsePanel(toks)
        return out
    }

    private fun parsePanel(toks: List<Token>): DocFields {
        val f = DocFields()
        f.name = field(toks, L_NAME)?.let { TextUtil.clean(it) }?.takeIf { it.isNotEmpty() }
        f.address = field(toks, L_ADDR)?.let { TextUtil.addr(it) }?.takeIf { it.isNotEmpty() }
        f.job = field(toks, L_JOB)?.let { normJob(it) }?.takeIf { it.isNotEmpty() }
        f.dob = field(toks, L_DOB)?.let { parseDate(it) }
        f.gender = field(toks, L_GENDER)?.let { parseGender(it) }
        f.age = parseAge(toks)
        return f
    }

    private fun isSep(c: Char): Boolean = c == ':' || c == '：' || c == ' '

    private fun field(toks: List<Token>, labels: List<String>): String? {
        for ((i, t) in toks.withIndex()) {
            val c = TextUtil.clean(t.text)
            if (c.isEmpty()) continue
            val fc = TextUtil.foldChars(c)
            val k = fc.trim { it == ':' || it == '：' || it == ' ' || it == '•' }
            if (k in labels) {
                val v = valueAfter(toks, i)
                if (v != null) return v
                continue
            }
            for (lk in labels) {
                if (fc.length > lk.length && fc.startsWith(lk) && isSep(fc[lk.length])) {
                    val v = c.substring(lk.length).trimStart(':', '：', ' ', '-', '•').trim()
                    if (v.isNotEmpty()) return v
                }
            }
        }
        return null
    }

    /** Value that belongs to a label token: same row on screen first, otherwise the next text in tree order. */
    private fun valueAfter(toks: List<Token>, i: Int): String? {
        val lab = toks[i]
        if (lab.hasBox) {
            var best: Token? = null
            var bestD = Int.MAX_VALUE
            for ((j, t) in toks.withIndex()) {
                if (j == i || !t.hasBox || isLabel(t.text)) continue
                if (abs(t.cy - lab.cy) <= maxOf(lab.h, t.h) * 0.6) {
                    val d = abs(t.cx - lab.cx)
                    if (d < bestD) {
                        bestD = d
                        best = t
                    }
                }
            }
            if (best != null) {
                val c = TextUtil.clean(best.text)
                if (c.isNotEmpty()) return c
            }
        }
        for (j in i + 1 until toks.size) {
            if (isLabel(toks[j].text)) return null
            val c = TextUtil.clean(toks[j].text)
            if (c.isNotEmpty()) return c
        }
        return null
    }

    private fun parseDate(v: String): String? {
        val s = TextUtil.clean(v)
        val a = DATE_YMD.find(s)
        if (a != null) {
            return "${a.groupValues[1].toInt()}-${a.groupValues[2].toInt()}-${a.groupValues[3].toInt()}"
        }
        val b = DATE_DMY.find(s)
        if (b != null) {
            return "${b.groupValues[3].toInt()}-${b.groupValues[2].toInt()}-${b.groupValues[1].toInt()}"
        }
        return null
    }

    private fun parseGender(v: String): String? {
        val k = TextUtil.key(v)
        return when {
            k.startsWith("ذكر") -> "ذكر"
            k.startsWith("انثي") -> "أنثى"
            else -> null
        }
    }

    private fun parseAge(toks: List<Token>): Int? {
        for (t in toks) {
            val m = AGE_RE.find(TextUtil.clean(t.text))
            if (m != null) return m.groupValues[1].toIntOrNull()
        }
        val v = field(toks, L_AGE) ?: return null
        return Regex("\\d{1,3}").find(v)?.value?.toIntOrNull()
    }

    /** Job without the internal code, e.g. "سائق (J02)" -> "سائق". */
    private fun normJob(v: String): String {
        var s = TextUtil.clean(v)
        s = CODE_PAREN.replace(s, " ")
        s = CODE_BARE.replace(s, " ")
        val r = TextUtil.clean(s)
        return if (r.isNotEmpty()) r else TextUtil.clean(v)
    }
}
