package com.dcorp.skyprint.template

/** Cấu hình tiền tệ cho formatter `money` (DESIGN-009's risk -- mặc định khớp VND minor=1/100). */
data class MoneyFormat(val minorUnits: Int = 100, val thousandsSeparator: String = ".", val suffix: String = "") {
    companion object { val VND = MoneyFormat() }
}

/**
 * Bộ định dạng `{{path | tên | tên:'tham số'}}` (DESIGN-009). Đăng ký cố
 * định, KHÔNG cho mẫu tự định nghĩa formatter mới (an toàn: mẫu chỉ dùng
 * dữ liệu app cấp + phép biến đổi đã kiểm soát, không chạy code tuỳ ý).
 */
object Formatters {
    fun apply(name: String, arg: String?, value: TemplateValue, money: MoneyFormat): TemplateValue = when (name) {
        "money" -> TemplateValue.Str(formatMoney(value, money))
        "number" -> TemplateValue.Str(formatNumber(value, arg?.toIntOrNull() ?: 0))
        "upper" -> TemplateValue.Str(value.displayString().uppercase())
        "lower" -> TemplateValue.Str(value.displayString().lowercase())
        "default" -> if (value.isTruthy()) value else TemplateValue.Str(arg.orEmpty())
        "truncate" -> {
            val n = arg?.toIntOrNull() ?: Int.MAX_VALUE
            TemplateValue.Str(value.displayString().take(n))
        }
        "pad" -> {
            val n = arg?.toIntOrNull() ?: 0
            TemplateValue.Str(value.displayString().padEnd(n))
        }
        "datetime" -> TemplateValue.Str(DateTimeFormatter.format(value.displayString(), arg.orEmpty()))
        else -> throw TemplateException(TemplateErrorCode.UNKNOWN_FORMATTER, "Formatter không tồn tại: '$name'")
    }

    /** Số nguyên phần triệu -> chuỗi (bỏ phần thập phân, khớp quy ước tiền VND không có sub-unit -- ReceiptFormatter.formatMinor bên skyprint-core). */
    private fun formatMoney(value: TemplateValue, money: MoneyFormat): String {
        val minor = (value as? TemplateValue.Num)?.value ?: value.displayString().toDoubleOrNull() ?: 0.0
        val whole = (minor / money.minorUnits).toLong()
        val sign = if (whole < 0) "-" else ""
        val digits = kotlin.math.abs(whole).toString()
        val grouped = digits.reversed().chunked(3).joinToString(money.thousandsSeparator).reversed()
        return "$sign$grouped${money.suffix}"
    }

    private fun formatNumber(value: TemplateValue, decimals: Int): String {
        val num = (value as? TemplateValue.Num)?.value ?: value.displayString().toDoubleOrNull() ?: 0.0
        if (decimals <= 0) return num.toLong().toString()
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        val rounded = kotlin.math.round(num * multiplier) / multiplier
        val text = rounded.toString()
        val dot = text.indexOf('.')
        return if (dot == -1) "$text." + "0".repeat(decimals)
        else {
            val fraction = text.substring(dot + 1).padEnd(decimals, '0').take(decimals)
            text.substring(0, dot) + "." + fraction
        }
    }
}

enum class TemplateErrorCode { INVALID_TEMPLATE, UNKNOWN_FORMATTER, UNKNOWN_VARIABLE, UNKNOWN_ELEMENT_TYPE }

class TemplateException(val code: TemplateErrorCode, message: String) : Exception(message)

/**
 * Phân giải chuỗi ISO-8601 tối thiểu (`YYYY-MM-DDTHH:MM:SS`, phần sau giây
 * bị bỏ qua) sang pattern tối giản (`yyyy MM dd HH mm ss`) -- không kéo
 * `kotlinx-datetime` vào chỉ để đổi định dạng hiển thị (DESIGN-009: giữ
 * engine gọn, không phụ thuộc thư viện ngày giờ nặng).
 */
internal object DateTimeFormatter {
    private val ISO_REGEX = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})""")

    fun format(iso: String, pattern: String): String {
        val match = ISO_REGEX.find(iso) ?: return iso
        val (year, month, day, hour, minute, second) = match.destructured
        return pattern
            .replace("yyyy", year)
            .replace("MM", month)
            .replace("dd", day)
            .replace("HH", hour)
            .replace("mm", minute)
            .replace("ss", second)
    }
}
