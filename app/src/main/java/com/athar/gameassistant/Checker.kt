package com.athar.gameassistant

object Checker {
    private val ALL = listOf(DocType.CARD, DocType.COMPANY, DocType.RESIDENCE)
    private val CARD_COMPANY = listOf(DocType.CARD, DocType.COMPANY)

    fun run(docs: Map<DocType, DocFields>): Report {
        if (docs.isEmpty()) {
            return Report(emptyList(), null, "مفيش ملفات موظف ظاهرة على الشاشة.")
        }
        val items = listOf(
            compare("الاسم", docs, ALL) { it.name },
            addressCheck(docs),
            compare("تاريخ الميلاد", docs, CARD_COMPANY) { it.dob },
            ageCheck(docs),
            compare("الوظيفة", docs, CARD_COMPANY) { it.job },
            compare("الجنس", docs, CARD_COMPANY) { it.gender }
        )
        val active = items.filter { it.status != Status.NA }
        val overall = when {
            active.any { it.status == Status.BAD } -> Status.BAD
            active.any { it.status == Status.UNKNOWN } -> Status.UNKNOWN
            else -> Status.OK
        }
        return Report(items, overall, null)
    }

    private fun compare(
        title: String,
        docs: Map<DocType, DocFields>,
        types: List<DocType>,
        pick: (DocFields) -> String?
    ): CheckItem {
        val values = types.map { it to docs[it]?.let(pick) }
        val missing = values.filter { it.second.isNullOrEmpty() }.map { it.first.short }
        if (missing.isNotEmpty()) {
            return CheckItem(title, Status.UNKNOWN, listOf("مش قادر أقرا في: " + missing.joinToString("، ")))
        }
        val vs = values.map { it.second ?: "" }
        if (vs.all { it == vs[0] }) return CheckItem(title, Status.OK)
        val ref = mode(vs)
        return CheckItem(
            title,
            Status.BAD,
            values.map { "${it.first.short}: ${markDiff(it.second ?: "", ref)}" }
        )
    }

    private fun addressCheck(docs: Map<DocType, DocFields>): CheckItem {
        val have = ALL.filter { !docs[it]?.address.isNullOrEmpty() }
        return when {
            have.size >= 2 -> compare("العنوان", docs, have) { it.address }
            have.size == 1 -> CheckItem(
                "العنوان", Status.NA,
                listOf("ظاهر في ${have[0].short} بس — مفيش ملف تاني فيه عنوان يتقارن بيه.")
            )
            else -> CheckItem("العنوان", Status.UNKNOWN, listOf("مش لاقي عنوان مقروء في أي ملف"))
        }
    }

    private fun ageCheck(docs: Map<DocType, DocFields>): CheckItem {
        val age = docs[DocType.CARD]?.age
            ?: return CheckItem("العمر", Status.UNKNOWN, listOf("مش قادر أقرا العمر من البطاقة"))
        return if (age in 18..65) {
            CheckItem("العمر ($age)", Status.OK)
        } else {
            CheckItem("العمر ($age)", Status.BAD, listOf("خارج الحد المسموح 18–65"))
        }
    }

    private fun mode(list: List<String>): String {
        val counts = LinkedHashMap<String, Int>()
        for (s in list) counts[s] = (counts[s] ?: 0) + 1
        var best = list[0]
        var bestN = 0
        for ((s, n) in counts) {
            if (n > bestN) {
                best = s
                bestN = n
            }
        }
        return best
    }

    /** Wraps the characters that differ from [ref] in [ ] (only when both have the same length). */
    private fun markDiff(v: String, ref: String): String {
        if (v == ref || v.length != ref.length) return v
        val sb = StringBuilder()
        for (i in v.indices) {
            if (v[i] == ref[i]) sb.append(v[i]) else sb.append('[').append(v[i]).append(']')
        }
        return sb.toString()
    }
}
