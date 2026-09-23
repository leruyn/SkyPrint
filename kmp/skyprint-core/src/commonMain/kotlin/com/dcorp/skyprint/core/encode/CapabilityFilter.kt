package com.dcorp.skyprint.core.encode

import com.dcorp.skyprint.core.model.Element
import com.dcorp.skyprint.core.model.PrinterCapabilities
import com.dcorp.skyprint.core.model.ReceiptDocument

/**
 * Lọc [ReceiptDocument] theo [PrinterCapabilities] (REQ-010) TRƯỚC khi đưa
 * vào [EscPosEncoder] -- máy in không có dao/két/còi/QR/mã vạch thì bỏ
 * hoặc thay thế phần tử tương ứng, không để [EscPosEncoder] phát byte lạ
 * xuống máy in không hiểu được (req.md acceptance: "không in ký tự rác").
 *
 * v1 chưa có QR raster fallback (cần [com.dcorp.skyprint.core.model.MonoBitmap]
 * từ REQ-002's rasterizer, hiện chưa cài) -- máy không hỗ trợ QR thì BỎ QUA
 * phần tử đó kèm cảnh báo, thay vì raster (DESIGN-010's "risk", theo dõi ở
 * REQ-002 xong mới nối lại).
 */
object CapabilityFilter {
    fun apply(document: ReceiptDocument, capabilities: PrinterCapabilities): Pair<ReceiptDocument, List<String>> {
        val warnings = mutableListOf<String>()
        val elements = document.elements.mapNotNull { element -> transform(element, capabilities, warnings) }
        return ReceiptDocument(document.paper, elements) to warnings
    }

    private fun transform(element: Element, caps: PrinterCapabilities, warnings: MutableList<String>): Element? = when (element) {
        is Element.Cut ->
            if (caps.cutter) element
            else {
                warnings += "Cut: máy in không có dao cắt, thay bằng feed 4 dòng để xé tay."
                Element.Feed(4)
            }

        is Element.QrCode ->
            if (caps.qr) element
            else {
                warnings += "QrCode: máy in không hỗ trợ QR, đã bỏ qua phần tử này (v1 chưa có raster fallback)."
                null
            }

        is Element.Barcode ->
            if (caps.barcode) element
            else {
                warnings += "Barcode: máy in không hỗ trợ mã vạch, đã bỏ qua phần tử này."
                null
            }

        is Element.Drawer ->
            if (caps.drawer) element
            else {
                warnings += "Drawer: máy in không có/không nối két tiền, đã bỏ qua lệnh mở két."
                null
            }

        is Element.Beep ->
            if (caps.buzzer) element
            else {
                warnings += "Beep: máy in không có còi, đã bỏ qua."
                null
            }

        is Element.Text ->
            if (caps.inverse || !element.style.inverse) element
            else {
                warnings += "Text.inverse: máy in không hỗ trợ đảo màu, in bình thường (không đảo)."
                element.copy(style = element.style.copy(inverse = false))
            }

        else -> element
    }
}
