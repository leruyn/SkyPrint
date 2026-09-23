package com.dcorp.skyprint.core.encode

/**
 * Bỏ dấu tiếng Việt về ASCII cho [com.dcorp.skyprint.core.model.TextMode.ASCII]
 * (REQ-001/REQ-002's req.md doc) -- máy in nhiệt rẻ tiền không đảm bảo hỗ
 * trợ UTF-8/codepage tiếng Việt trong ROM. Ký tự ngoài bảng và ngoài ASCII
 * in được (0x20..0x7E) bị thay bằng `?` thay vì lọt UTF-8 nhiều byte xuống
 * máy in (dễ ra chuỗi rác không đoán trước được), khớp acceptance criteria
 * của req.md.
 */
object AsciiFolding {
    fun fold(input: String): String = buildString(input.length) {
        for (ch in input) {
            val mapped = VIETNAMESE_MAP[ch]
            when {
                mapped != null -> append(mapped)
                ch.code in 0x20..0x7E -> append(ch)
                ch == '\n' -> append(ch)
                else -> append('?')
            }
        }
    }

    private val VIETNAMESE_MAP: Map<Char, Char> = buildMap {
        val groups = listOf(
            'a' to "àáảãạăằắẳẵặâầấẩẫậ",
            'e' to "èéẻẽẹêềếểễệ",
            'i' to "ìíỉĩị",
            'o' to "òóỏõọôồốổỗộơờớởỡợ",
            'u' to "ùúủũụưừứửữự",
            'y' to "ỳýỷỹỵ",
            'd' to "đ",
        )
        for ((base, accented) in groups) {
            for (ch in accented) {
                put(ch, base)
                put(ch.uppercaseChar(), base.uppercaseChar())
            }
        }
    }
}
