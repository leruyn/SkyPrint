package com.dcorp.skyprint.raster

/** Ngắt dòng dùng chung cho mọi [TextMeasurer]: ngắt ở khoảng trắng, từ dài hơn [maxWidth] thì cắt theo ký tự. */
object TextWrap {
    fun wrap(text: String, maxWidth: Int, width: (String) -> Int): List<String> {
        val out = ArrayList<String>()
        for (paragraph in text.split('\n')) {
            var line = ""
            for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (width(candidate) <= maxWidth) { line = candidate; continue }
                if (line.isNotEmpty()) { out += line; line = "" }
                var rest = word
                while (width(rest) > maxWidth && rest.length > 1) {
                    var n = rest.length - 1
                    while (n > 1 && width(rest.substring(0, n)) > maxWidth) n--
                    out += rest.substring(0, n); rest = rest.substring(n)
                }
                line = rest
            }
            out += line
        }
        return out.ifEmpty { listOf("") }
    }
}
