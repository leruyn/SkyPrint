package com.dcorp.skyprint.core.encode

import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import com.dcorp.skyprint.core.model.Element
import com.dcorp.skyprint.core.model.ReceiptDocument
import com.dcorp.skyprint.core.model.TextMode

/**
 * Chuyển [ReceiptDocument] thành byte ESC/POS (REQ-001, DESIGN-001).
 * [TextMode.RASTER] cần một bộ vẽ chữ theo nền tảng -- chưa cài ở CODE-001,
 * xem REQ-002/CODE-002.
 */
object EscPosEncoder {
    fun encode(document: ReceiptDocument, mode: TextMode): ByteArray {
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
                    raw(EscPosCommands.align(element.style.align))
                    if (element.style.bold) raw(EscPosCommands.bold(true))
                    if (element.style.size != 1) raw(EscPosCommands.size(element.style.size))
                    line(element.text)
                    if (element.style.bold) raw(EscPosCommands.bold(false))
                    if (element.style.size != 1) raw(EscPosCommands.size(1))
                }

                is Element.Row -> line(LayoutEngine.renderRow(element.columns, width))

                is Element.Divider -> line(LayoutEngine.dividerLine(element.char, width))

                is Element.Feed -> raw(EscPosCommands.feed(element.lines))

                is Element.QrCode -> {
                    raw(EscPosCommands.align(element.align))
                    raw(EscPosCommands.qrCode(element.data, element.moduleSize, element.errorCorrection))
                }

                is Element.Image -> throw PrinterException(
                    code = PrinterErrorCode.UNSUPPORTED_PLATFORM,
                    message = "Element.Image chưa cài ở REQ-001 (raster packer thuộc REQ-002).",
                )

                Element.Cut -> raw(EscPosCommands.CUT)
            }
        }

        return out.toByteArray()
    }
}
