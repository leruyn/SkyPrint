package com.dcorp.skyprint.core.encode

import com.dcorp.skyprint.core.model.Element
import com.dcorp.skyprint.core.model.PaperWidth
import com.dcorp.skyprint.core.model.PrinterCapabilities
import com.dcorp.skyprint.core.model.ReceiptDocument
import com.dcorp.skyprint.core.model.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TEST-UNIT-010 (bổ sung) -- CapabilityFilter.
 * Lưu ý quy trình: test này viết SAU khi CapabilityFilter đã có code (lệch
 * thứ tự TDD chuẩn của WORKFLOW.md), ghi nhận công khai thay vì giấu đi.
 */
class CapabilityFilterTest {

    private fun doc(vararg elements: Element) = ReceiptDocument(PaperWidth.MM58, elements.toList())

    @Test
    fun `may in khong co dao cat thay Cut bang Feed 4 dong va canh bao`() {
        val (result, warnings) = CapabilityFilter.apply(doc(Element.Cut()), PrinterCapabilities(cutter = false))
        assertEquals(listOf(Element.Feed(4)), result.elements)
        assertTrue(warnings.any { it.contains("Cut") })
    }

    @Test
    fun `may in co dao cat thi giu nguyen Cut khong canh bao`() {
        val (result, warnings) = CapabilityFilter.apply(doc(Element.Cut()), PrinterCapabilities(cutter = true))
        assertEquals(listOf(Element.Cut()), result.elements)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `may in khong ho tro QR bo qua phan tu kem canh bao`() {
        val (result, warnings) = CapabilityFilter.apply(doc(Element.QrCode("x")), PrinterCapabilities(qr = false))
        assertTrue(result.elements.isEmpty())
        assertTrue(warnings.any { it.contains("QrCode") })
    }

    @Test
    fun `may in khong co ket bo qua Drawer khong con Beep neu buzzer tat`() {
        val caps = PrinterCapabilities(drawer = false, buzzer = false)
        val (result, warnings) = CapabilityFilter.apply(doc(Element.Drawer(), Element.Beep()), caps)
        assertTrue(result.elements.isEmpty())
        assertEquals(2, warnings.size)
    }

    @Test
    fun `may in khong ho tro dao mau in binh thuong khong bo phan tu`() {
        val (result, warnings) = CapabilityFilter.apply(
            doc(Element.Text("HUY", TextStyle(inverse = true))),
            PrinterCapabilities(inverse = false),
        )
        val text = result.elements.single() as Element.Text
        assertEquals(false, text.style.inverse)
        assertTrue(warnings.any { it.contains("inverse") })
    }

    @Test
    fun `mac dinh khong tham so bat het tru drawer va buzzer`() {
        val caps = PrinterCapabilities()
        assertEquals(true, caps.cutter)
        assertEquals(true, caps.qr)
        assertEquals(true, caps.barcode)
        assertEquals(true, caps.inverse)
        assertEquals(false, caps.drawer)
        assertEquals(false, caps.buzzer)
    }
}
