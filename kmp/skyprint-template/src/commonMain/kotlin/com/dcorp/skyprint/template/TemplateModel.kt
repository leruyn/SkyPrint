package com.dcorp.skyprint.template

/**
 * Cây mẫu in đã parse từ JSON (DESIGN-009) -- một "bag of nullable fields"
 * thay vì phân cấp lớp riêng từng loại phần tử: JSON mẫu vốn dị hình theo
 * `type`, và field nào cũng có thể mang placeholder cần nội suy trước khi
 * biết nó hợp lệ hay không, nên tách lớp sẽ chỉ dời việc kiểm tra `type`
 * từ đây sang chỗ khác chứ không giảm được gì.
 */
data class TemplateNode(
    val type: String,
    val enabled: Boolean = true,
    val each: String? = null,
    val asVar: String? = null,
    val ifExpr: String? = null,
    // text / qr / barcode / image
    val value: String? = null,
    val style: TemplateStyle? = null,
    // row
    val columns: List<TemplateColumn>? = null,
    val header: Boolean = false,
    // divider
    val char: String = "-",
    // feed
    val lines: Int = 1,
    // cut
    val partial: Boolean = true,
    // qr
    val moduleSize: Int = 6,
    val errorCorrection: String = "M",
    val caption: String? = null,
    // barcode
    val barcodeType: String = "CODE128",
    val heightDots: Int = 80,
    val hri: Boolean = true,
    // drawer
    val pin: Int = 0,
    val pulseMs: Int = 100,
    // beep
    val times: Int = 1,
    val durationMs: Int = 100,
    // group
    val elements: List<TemplateNode>? = null,
    // align dùng chung cho qr/barcode/image (text dùng style.align)
    val align: String = "CENTER",
)

data class TemplateStyle(
    val align: String = "LEFT",
    val bold: Boolean = false,
    val width: Int = 1,
    val height: Int = 1,
    val inverse: Boolean = false,
    val underline: Boolean = false,
)

data class TemplateColumn(val text: String, val weight: Int = 1, val align: String = "LEFT", val bold: Boolean = false)

data class Template(
    val id: String,
    val version: Int,
    val documentType: String,
    val paper: String?,
    val elements: List<TemplateNode>,
)

/** Một vấn đề tìm thấy lúc [TemplateEngine.validate] hoặc cảnh báo lúc [TemplateEngine.render]. */
data class TemplateIssue(val where: String, val message: String)

/** Khai báo dữ liệu hợp lệ cho một loại chứng từ (RECEIPT, PRECHECK, KITCHEN...) -- DESIGN-009. */
data class TemplateSchema(val documentType: String, val paths: Set<String>, val sample: TemplateValue)
