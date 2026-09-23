package com.dcorp.skyprint.core.model

/** Lệnh còi khác nhau giữa hãng máy in (DESIGN-010's risk) -- cấu hình theo máy thay vì đoán một chuẩn chung. */
enum class BuzzerCommand { ESC_B, EPSON_A }

/**
 * Năng lực phần cứng một máy in khai báo (REQ-010). Mặc định không tham số
 * bật hết TRỪ [drawer]/[buzzer] -- đúng theo precondition của req.md
 * ("App khai PrinterCapabilities cho từng máy in; mặc định: bật hết trừ
 * drawer/buzzer") vì phần lớn máy in KHÔNG có 2 phần cứng này gắn kèm, còn
 * cutter/qr/barcode/inverse gần như luôn có trên máy in nhiệt hiện đại.
 */
data class PrinterCapabilities(
    val cutter: Boolean = true,
    val drawer: Boolean = false,
    val buzzer: Boolean = false,
    val qr: Boolean = true,
    val barcode: Boolean = true,
    val inverse: Boolean = true,
    val buzzerCommand: BuzzerCommand = BuzzerCommand.ESC_B,
) {
    companion object {
        /** Dùng khi biết chắc máy in có đủ mọi phần cứng (test, hoặc máy in cao cấp đã kiểm chứng). */
        val ALL = PrinterCapabilities(cutter = true, drawer = true, buzzer = true, qr = true, barcode = true, inverse = true)
    }
}
