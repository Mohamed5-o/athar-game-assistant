package com.athar.gameassistant

/** The three documents shown on the game screen. */
enum class DocType(val short: String) {
    CARD("البطاقة"),
    COMPANY("الشركة"),
    RESIDENCE("السكن")
}

/** One piece of text read from the screen, with its on-screen box (0 if unknown). */
class Token(
    val text: String,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val visible: Boolean = true
) {
    val hasBox: Boolean get() = right > left && bottom > top
    val cx: Int get() = (left + right) / 2
    val cy: Int get() = (top + bottom) / 2
    val h: Int get() = bottom - top
}

/** Values extracted from one document. null = not found / not readable. */
class DocFields {
    var name: String? = null
    var address: String? = null
    var job: String? = null
    var dob: String? = null      // normalised as "year-month-day" with plain integers
    var gender: String? = null   // "ذكر" or "أنثى"
    var age: Int? = null
}

enum class Status { OK, BAD, UNKNOWN, NA }

class CheckItem(
    val title: String,
    val status: Status,
    val lines: List<String> = emptyList()
)

class Report(
    val items: List<CheckItem>,
    val overall: Status?,
    val note: String?
) {
    fun signature(): String =
        items.joinToString("|") { "${it.title}:${it.status}:${it.lines.joinToString(",")}" } + "#" + (note ?: "")
}
