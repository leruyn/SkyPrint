package com.dcorp.skyprint.core.encode

import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import com.dcorp.skyprint.core.model.BuzzerCommand
import com.dcorp.skyprint.core.model.Element
import com.dcorp.skyprint.core.model.ReceiptDocument
import com.dcorp.skyprint.core.model.TextMode
import com.dcorp.skyprint.core.raster.RasterBlock
import com.dcorp.skyprint.core.raster.RasterLine
import com.dcorp.skyprint.core.raster.RasterPacker
import com.dcorp.skyprint.core.raster.RasterSpan
import com.dcorp.skyprint.core.raster.TextRasterizer

/**
 * Chuyển [ReceiptDocument] thành byte ESC/POS (REQ-001/DESIGN-001, mở rộng
 * nghiệp vụ POS ở DESIGN-010, chữ raster ở DESIGN-002).
 *
 * [TextMode.RASTER] gom các [Element.Text]/[Element.Row]/[Element.Divider]
 * LIÊN TIẾP thành một [com.dcorp.skyprint.core.raster.RasterBlock], gọi
 * [rasterizer] một lần cho cả nhóm (ít lệnh `GS v 0` hơn, in nhanh hơn so
 * với raster từng dòng riêng lẻ), rồi đóng gói bằng [RasterPacker]. Phần
 * tử khác (Cut/QrCode/Barcode/Drawer/Beep/Image) luôn ngắt nhóm đang gom dở.
 * [rasterizer] `null` -> [PrinterErrorCode.UNSUPPORTED_PLATFORM] ngay từ
 * đầu, không phát byte nào (chưa có bộ vẽ chữ thật theo nền tảng ở CODE-002,
 * xem CHANGELOG).
 *
 * [Element.Image] luôn dùng [RasterPacker] bất kể [mode] -- ảnh vốn đã là
 * bitmap sẵn, không cần "vẽ chữ" nên không phụ thuộc [rasterizer].
 *
 * [buzzerCommand]: chọn biến thể lệnh còi khớp máy in thật (DESIGN-010's
 * risk -- không có chuẩn chung giữa các hãng); không thuộc [PrinterCapabilities]
 * (đó là bộ lọc bật/tắt tính năng, không phải chọn cú pháp lệnh) nên truyền
 * trực tiếp vào đây, mặc định [BuzzerCommand.ESC_B] vì phổ biến nhất.
 */
object EscPosEncoder {
    fun encode(
        document: ReceiptDocument,
        mode: TextMode,
        buzzerCommand: BuzzerCommand = BuzzerCommand.ESC_B,
        rasterizer: TextRasterizer? = null,
    ): ByteArray {
        if (mode == TextMode.RASTER && rasterizer == null) {
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
        val pendingRasterLines = mutableListOf<RasterLine>()

        fun flushRaster() {
            if (pendingRasterLines.isEmpty()) return
            val bitmap = rasterizer!!.render(RasterBlock(pendingRasterLines.toList()), document.paper.dots)
            raw(RasterPacker.toGsV0(bitmap))
            pendingRasterLines.clear()
        }

        for (element in document.elements) {
            when (element) {
                is Element.Text -> if (mode == TextMode.RASTER) {
                    val style = element.style
                    pendingRasterLines += RasterLine(
                        spans = listOf(
                            RasterSpan(
                                text = element.text,
                                bold = style.bold,
                                underline = style.underline,
                                inverse = style.inverse,
                                widthScale = style.width,
                                heightScale = style.height,
                            ),
                        ),
                        align = style.align,
                    )
                } else {
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

                is Element.Row -> if (mode == TextMode.RASTER) {
                    pendingRasterLines += RasterLine(listOf(RasterSpan(LayoutEngine.renderRow(element.columns, width))))
                } else {
                    line(LayoutEngine.renderRow(element.columns, width))
                }

                is Element.Divider -> if (mode == TextMode.RASTER) {
                    pendingRasterLines += RasterLine(listOf(RasterSpan(LayoutEngine.dividerLine(element.char, width))))
                } else {
                    line(LayoutEngine.dividerLine(element.char, width))
                }

                is Element.Feed -> { flushRaster(); raw(EscPosCommands.feed(element.lines)) }

                is Element.QrCode -> {
                    flushRaster()
                    raw(EscPosCommands.align(element.align))
                    raw(EscPosCommands.qrCode(element.data, element.moduleSize, element.errorCorrection))
                }

                is Element.Barcode -> {
                    flushRaster()
                    raw(EscPosCommands.align(element.align))
                    raw(EscPosCommands.barcodeHri(element.hri))
                    raw(EscPosCommands.barcodeHeight(element.heightDots))
                    raw(EscPosCommands.barcode(element.type, element.data))
                }

                is Element.Drawer -> { flushRaster(); raw(EscPosCommands.drawer(element.pin, element.pulseMs)) }

                is Element.Beep -> { flushRaster(); raw(EscPosCommands.beep(buzzerCommand, element.times, element.durationMs)) }

                is Element.Image -> {
                    flushRaster()
                    raw(EscPosCommands.align(element.align))
                    raw(RasterPacker.toGsV0(element.bitmap))
                }

                is Element.Cut -> { flushRaster(); raw(EscPosCommands.cut(element.partial)) }
            }
        }
        flushRaster()

        return out.toByteArray()
    }
}
