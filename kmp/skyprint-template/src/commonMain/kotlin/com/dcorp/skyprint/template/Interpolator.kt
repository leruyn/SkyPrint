package com.dcorp.skyprint.template

/**
 * Thay `{{ path | fmt | fmt2:'arg' }}` trong chuỗi mẫu bằng giá trị thật
 * (DESIGN-009). Văn bản ngoài `{{...}}` giữ nguyên; nhiều placeholder
 * trong cùng chuỗi được nối lại.
 */
object Interpolator {
    // `}` PHẢI escape: regex Android (ICU) ném PatternSyntaxException với `}` trần, JVM thì chấp nhận -- test JVM không bắt được.
    private val PLACEHOLDER_REGEX = Regex("""\{\{\s*(.*?)\s*\}\}""")

    fun interpolate(scope: Scope, template: String, money: MoneyFormat): String =
        PLACEHOLDER_REGEX.replace(template) { match -> resolveExpression(scope, match.groupValues[1], money) }

    /** [path] không có `|` -- trả giá trị thô đã resolve (dùng khi cả biểu thức, không phải chuỗi có chữ bao quanh, chỉ là 1 path -- ví dụ điều kiện `if`). */
    fun resolveValue(scope: Scope, path: String): TemplateValue = PathResolver.resolve(scope, path)

    private fun resolveExpression(scope: Scope, expr: String, money: MoneyFormat): String {
        val parts = splitPipes(expr)
        var value = PathResolver.resolve(scope, parts.first().trim())
        for (stage in parts.drop(1)) {
            val (name, arg) = parseFormatterCall(stage.trim())
            value = Formatters.apply(name, arg, value, money)
        }
        return value.displayString()
    }

    /** Tách theo `|` ở cấp ngoài cùng -- KHÔNG tách `|` nằm trong chuỗi `'...'` của tham số formatter (vd `datetime:'HH:mm|ss'` giả định, dù hiếm gặp). */
    private fun splitPipes(expr: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        for (ch in expr) {
            when {
                ch == '\'' -> { inQuote = !inQuote; current.append(ch) }
                ch == '|' && !inQuote -> { parts += current.toString(); current.clear() }
                else -> current.append(ch)
            }
        }
        parts += current.toString()
        return parts
    }

    /** `tên` hoặc `tên:'tham số'`. */
    private fun parseFormatterCall(stage: String): Pair<String, String?> {
        val colon = stage.indexOf(':')
        if (colon == -1) return stage to null
        val name = stage.substring(0, colon).trim()
        val rawArg = stage.substring(colon + 1).trim()
        val arg = rawArg.removeSurrounding("'").let { if (it == rawArg) rawArg.removeSurrounding("\"") else it }
        return name to arg
    }
}
