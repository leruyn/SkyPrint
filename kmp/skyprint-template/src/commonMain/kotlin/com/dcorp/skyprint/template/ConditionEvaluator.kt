package com.dcorp.skyprint.template

/**
 * Điều kiện `if` trong mẫu (DESIGN-009). Ngữ pháp CỐ Ý tối giản, không có
 * dấu ngoặc, không gọi hàm -- an toàn, dễ kiểm, đủ cho nghiệp vụ hoá đơn:
 *
 * ```
 * cond := term (('&&'|'||') term)*      -- trái sang phải, không phân biệt độ ưu tiên
 * term := '!'? path (op literal)?
 * op   := '==' | '!=' | '>' | '>=' | '<' | '<='
 * ```
 */
object ConditionEvaluator {
    private val OP_SPLIT = Regex("""\s*(&&|\|\|)\s*""")
    private val TERM_REGEX = Regex(
        """^(!)?\s*([^\s!=<>]+)\s*(?:(==|!=|>=|<=|>|<)\s*(.+))?$""",
    )

    fun evaluate(scope: Scope, expr: String): Boolean {
        val tokens = OP_SPLIT.split(expr.trim())
        val operators = OP_SPLIT.findAll(expr.trim()).map { it.groupValues[1] }.toList()

        var result = evaluateTerm(scope, tokens.first())
        for ((index, op) in operators.withIndex()) {
            val next = evaluateTerm(scope, tokens[index + 1])
            result = if (op == "&&") result && next else result || next
        }
        return result
    }

    private fun evaluateTerm(scope: Scope, rawTerm: String): Boolean {
        val term = rawTerm.trim()
        val match = TERM_REGEX.find(term)
            ?: throw TemplateException(TemplateErrorCode.INVALID_TEMPLATE, "Điều kiện không hợp lệ: '$term'")

        val negate = match.groupValues[1] == "!"
        val path = match.groupValues[2]
        val op = match.groupValues[3].ifEmpty { null }
        val literalRaw = match.groupValues[4].ifEmpty { null }

        val left = PathResolver.resolve(scope, path)
        val truthy = if (op == null) {
            left.isTruthy()
        } else {
            compare(left, parseLiteral(literalRaw ?: ""), op)
        }
        return if (negate) !truthy else truthy
    }

    private fun compare(left: TemplateValue, right: TemplateValue, op: String): Boolean = when (op) {
        "==" -> valuesEqual(left, right)
        "!=" -> !valuesEqual(left, right)
        ">", ">=", "<", "<=" -> {
            val l = numberOf(left)
            val r = numberOf(right)
            when (op) {
                ">" -> l > r
                ">=" -> l >= r
                "<" -> l < r
                else -> l <= r
            }
        }
        else -> false
    }

    private fun valuesEqual(a: TemplateValue, b: TemplateValue): Boolean = when {
        a is TemplateValue.Num || b is TemplateValue.Num -> numberOf(a) == numberOf(b)
        else -> a.displayString() == b.displayString()
    }

    private fun numberOf(value: TemplateValue): Double = when (value) {
        is TemplateValue.Num -> value.value
        is TemplateValue.Str -> value.value.toDoubleOrNull() ?: 0.0
        is TemplateValue.Bool -> if (value.value) 1.0 else 0.0
        else -> 0.0
    }

    private fun parseLiteral(raw: String): TemplateValue = when {
        raw == "true" -> TemplateValue.Bool(true)
        raw == "false" -> TemplateValue.Bool(false)
        raw == "null" -> TemplateValue.Null
        raw.startsWith("'") && raw.endsWith("'") -> TemplateValue.Str(raw.removeSurrounding("'"))
        raw.startsWith("\"") && raw.endsWith("\"") -> TemplateValue.Str(raw.removeSurrounding("\""))
        else -> raw.toDoubleOrNull()?.let { TemplateValue.Num(it) } ?: TemplateValue.Str(raw)
    }
}
