package com.dcorp.skyprint.core.encode

import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import com.dcorp.skyprint.core.model.BuzzerCommand
import com.dcorp.skyprint.core.model.Element
import com.dcorp.skyprint.core.model.ReceiptDocument
import com.dcorp.skyprint.core.model.TextMode

/**
 * Chuyển [ReceiptDocument] thành byte ESC/POS (REQ-001, DESIGN-001; mở
 * rộng nghiệp vụ POS ở DESIGN-010). [TextMode.RASTER] cần một bộ vẽ chữ
 * theo nền tảng -- chưa cài ở CODE-001, xem REQ-002/CODE-002.
 *
 * [buzzerCommand]: chọn biến thể lệnh còi khớp máy in thật (DESIGN-010's
 * risk -- không có chuẩn chung giữa các hãng); không thuộc [PrinterCapabilities]
 * (đó là bộ lọc bật/tắt tính năng, không phải chọn cú pháp lệnh) nên truyền
 * trực tiếp vào đây, mặc định [BuzzerCommand.ESC_B] vì phổ biến nhất.
 */
object EscPosEncoder {
    fun encode(document: ReceiptDocument, mode: TextMode, buzzerCommand: BuzzerCommand = BuzzerCommand.ESC_B): ByteArray {
        if (mode == TextMode.RASTER) {
            throw PrinterException(
                code = PrinterErrorCode.UNSUPPORTED_PLATFORM,
                message = "TextMode.RASTER cần TextRasterizer theo nền tảng, chưa cài ở REQ-001 (xem REQ-002).",
            )
        }

        val out = mutableListOf<Byte>()
        fun raw(bytes: ByteArray) { out.addAll(bytes.toList()) }
        fun text(s: String) = raw(AsciiFolding.fold(s).encodeToByteArray())
        fun line(s: String) { text(s); raw(EscPosCommands.LINE_FEED) }

        raw(EscPosCommands.INIT)
        val width = document.paper.columns

        for (element in document.elements) {
            when (element) {
                is Element.Text -> {
                    val style = element.style
                    raw(EscPosCommands.align(style.align))
                    if (style.bold) raw(EscPosCommands.bold(true))
                    if (style.underline) raw(EscPosCommands.underline(true))
                    if (style.inverse) raw(EscPosCommands.inverse(true))
                    if (style.width != 1 || style.height != 1) raw(EscPosCommands.charSize(style.width, style.height))
                    line(element.text)
                    if (style.width != 1 || style.height != 1) raw(EscPosCommands.charSize(1, 1))
                    if (style.inverse) raw(EscPosCommands.inverse(false))
                    if (style.underline) raw(EscPosCommands.underline(false))
                    if (style.bold) raw(EscPosCommands.bold(false))
                }

                is Element.Row -> line(LayoutEngine.renderRow(element.columns, width))

                is Element.Divider -> line(LayoutEngine.dividerLine(element.char, width))

                is Element.Feed -> raw(EscPosCommands.feed(element.lines))

                is Element.QrCode -> {
                    raw(EscPosCommands.align(element.align))
                    raw(EscPosCommands.qrCode(element.data, element.moduleSize, element.errorCorrection))
                }

                is Element.Barcode -> {
                    raw(EscPosCommands.align(element.align))
                    raw(EscPosCommands.barcodeHri(element.hri))
                    raw(EscPosCommands.barcodeHeight(element.heightDots))
                    raw(EscPosCommands.barcode(element.type, element.data))
                }

                is Element.Drawer -> raw(EscPosCommands.drawer(element.pin, element.pulseMs))

                is Element.Beep -> raw(EscPosCommands.beep(buzzerCommand, element.times, element.durationMs))

                is Element.Image -> throw PrinterException(
                    code = PrinterErrorCode.UNSUPPORTED_PLATFORM,
                    message = "Element.Image chưa cài ở REQ-001 (raster packer thuộc REQ-002).",
                )

                is Element.Cut -> raw(EscPosCommands.cut(element.partial))
            }
        }

        return out.toByteArray()
    }
}
