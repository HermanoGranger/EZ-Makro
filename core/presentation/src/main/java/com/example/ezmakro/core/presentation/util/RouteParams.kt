package com.example.ezmakro.core.presentation.util

import java.io.ByteArrayOutputStream

/**
 * Percent-encodes a value so it can be embedded into a string route without
 * breaking path or query delimiters (`/`, `?`, `&`, `=`). Behaviorally
 * identical to `android.net.Uri.encode(value)`: letters, digits and the
 * default allow set (`_-!.~'()*`) stay literal, everything else is encoded
 * as UTF-8 `%XX` sequences.
 */
fun encodeRouteParam(value: String): String {
    if (value.isEmpty()) return value
    val sb = StringBuilder(value.length)
    for (ch in value) {
        if (isUnescaped(ch)) {
            sb.append(ch)
        } else {
            for (byte in ch.toString().toByteArray(Charsets.UTF_8)) {
                val unsigned = byte.toInt() and BYTE_MASK
                sb
                    .append('%')
                    .append(HEX_DIGITS[unsigned ushr NYBBLE_SHIFT])
                    .append(HEX_DIGITS[unsigned and NYBBLE_MASK])
            }
        }
    }
    return sb.toString()
}

/**
 * Percent-decodes a value read back from a route argument. Behaviorally
 * identical to `android.net.Uri.decode(value)`: `%XX` sequences are decoded
 * as UTF-8, invalid sequences are passed through literally.
 */
fun decodeRouteParam(value: String): String {
    if ('%' !in value) return value
    val sb = StringBuilder(value.length)
    val bytes = ByteArrayOutputStream()

    fun flushBytes() {
        if (bytes.size() == 0) return
        sb.append(bytes.toByteArray().toString(Charsets.UTF_8))
        bytes.reset()
    }

    var i = 0
    while (i < value.length) {
        val ch = value[i]
        if (ch == '%' && i + 2 < value.length && areHexDigits(value[i + 1], value[i + 2])) {
            bytes.write((hexValue(value[i + 1]) shl NYBBLE_SHIFT) or hexValue(value[i + 2]))
            i += PERCENT_SEQUENCE_LENGTH
        } else {
            flushBytes()
            sb.append(ch)
            i++
        }
    }
    flushBytes()
    return sb.toString()
}

private fun isUnescaped(ch: Char): Boolean = isAsciiLetterOrDigit(ch) || DEFAULT_UNESCAPED.contains(ch)

private fun isAsciiLetterOrDigit(ch: Char): Boolean = ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9'

private fun areHexDigits(
    a: Char,
    b: Char,
): Boolean = Character.digit(a, HEX_RADIX) >= 0 && Character.digit(b, HEX_RADIX) >= 0

private fun hexValue(c: Char): Int = Character.digit(c, HEX_RADIX)

private const val DEFAULT_UNESCAPED = "_-!.~'()*"
private const val HEX_DIGITS = "0123456789ABCDEF"
private const val BYTE_MASK = 0xFF
private const val NYBBLE_SHIFT = 4
private const val NYBBLE_MASK = 0xF
private const val PERCENT_SEQUENCE_LENGTH = 3
private const val HEX_RADIX = 16
