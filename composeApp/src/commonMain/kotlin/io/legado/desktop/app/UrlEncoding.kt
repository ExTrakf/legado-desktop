package io.legado.desktop.app

/** 纯 Kotlin URL 百分号编码（commonMain 无 java.net.URLEncoder） */
fun urlEncode(s: String): String {
    val hex = "0123456789ABCDEF"
    return buildString {
        s.forEach { c ->
            if (c.isLetterOrDigit() || c in "-_.~") {
                append(c)
            } else {
                c.toString().encodeToByteArray().forEach { b ->
                    append('%')
                    append(hex[(b.toInt() ushr 4) and 0xF])
                    append(hex[b.toInt() and 0xF])
                }
            }
        }
    }
}
