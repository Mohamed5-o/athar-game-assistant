package com.athar.gameassistant

import java.text.Normalizer

object TextUtil {
    private val invisible = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]")
    private val marks = Regex("[\\u0640\\u064B-\\u065F\\u0670]")
    private val spaces = Regex("\\s+")
    private val punct = Regex("[،,;؛.\\-–—/\\\\|]+")

    private fun asciiDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                in '\u0660'..'\u0669' -> sb.append('0' + (c - '\u0660'))
                in '\u06F0'..'\u06F9' -> sb.append('0' + (c - '\u06F0'))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Light clean-up used for VALUES. It never changes letters, so dot differences
     * (ب/ت/ث/ن/ي, ج/ح/خ, ف/ق ...) are kept and will be reported as mismatches.
     * Only invisible direction marks, tatweel, harakat and extra spaces are removed,
     * and digits are converted to 0-9.
     */
    fun clean(s: String): String {
        var t = Normalizer.normalize(s, Normalizer.Form.NFKC)
        t = invisible.replace(t, "")
        t = marks.replace(t, "")
        t = asciiDigits(t)
        return spaces.replace(t, " ").trim()
    }

    /** Same length as the input: only used to recognise LABELS (alef/yaa/taa-marbuta variants). */
    fun foldChars(c: String): String {
        val sb = StringBuilder(c.length)
        for (ch in c) {
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ى' -> sb.append('ي')
                'ة' -> sb.append('ه')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun key(s: String): String =
        foldChars(clean(s)).trim { it == ':' || it == '：' || it == ' ' || it == '•' }

    /** Address text with punctuation ignored. */
    fun addr(s: String): String =
        spaces.replace(punct.replace(clean(s), " "), " ").trim()
}
