package com.athar.gameassistant

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

    companion object {
        fun of(items: List<CheckItem>): Report {
            val active = items.filter { it.status != Status.NA }
            val overall = when {
                active.any { it.status == Status.BAD } -> Status.BAD
                active.any { it.status == Status.UNKNOWN } -> Status.UNKNOWN
                else -> Status.OK
            }
            return Report(items, overall, null)
        }

        fun note(text: String): Report = Report(emptyList(), null, text)
    }
}
