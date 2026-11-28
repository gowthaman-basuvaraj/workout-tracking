package com.gowthaman.workout.ui

/**
 * Percent-encoding for navigation arguments.
 *
 * Exercise, food and stress names are whatever someone typed, and they travel through a
 * route string that already uses `/`, `?` and `&` structurally. "Bench press" is harmless;
 * "Push/pull" would otherwise split the route in two and "Salt & pepper" would start a
 * second argument.
 *
 * This replaces `android.net.Uri`'s encoder, which is the only thing navigation needed
 * from the platform. Narrower than that one on purpose: it escapes everything outside the
 * RFC 3986 unreserved set rather than trying to guess which characters are safe in which
 * part of a URL. Encoding more than strictly necessary costs nothing here, since the same
 * pair of functions is on both sides.
 */
internal object UrlCode {

    private const val UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"

    private const val HEX = "0123456789ABCDEF"

    fun encode(raw: String): String {
        val out = StringBuilder(raw.length)
        // UTF-8 first, so a name in any script survives the trip as bytes.
        for (byte in raw.encodeToByteArray()) {
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            if (char in UNRESERVED) {
                out.append(char)
            } else {
                out.append('%').append(HEX[value shr 4]).append(HEX[value and 0x0F])
            }
        }
        return out.toString()
    }

    fun decode(encoded: String): String {
        val bytes = ArrayList<Byte>(encoded.length)
        var i = 0
        while (i < encoded.length) {
            val char = encoded[i]
            when {
                char == '%' && i + 2 < encoded.length -> {
                    val hi = HEX.indexOf(encoded[i + 1].uppercaseChar())
                    val lo = HEX.indexOf(encoded[i + 2].uppercaseChar())
                    if (hi >= 0 && lo >= 0) {
                        bytes.add(((hi shl 4) or lo).toByte())
                        i += 3
                    } else {
                        // Not a real escape -- a literal % someone typed. Keep it.
                        bytes.addAll(char.toString().encodeToByteArray().toList())
                        i++
                    }
                }
                else -> {
                    bytes.addAll(char.toString().encodeToByteArray().toList())
                    i++
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}
