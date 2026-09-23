package com.dcorp.skyprint.core.encode

import com.dcorp.skyprint.core.model.Align
import com.dcorp.skyprint.core.model.BarcodeType
import com.dcorp.skyprint.core.model.Element
import com.dcorp.skyprint.core.model.PaperWidth
import com.dcorp.skyprint.core.model.ReceiptDocument
import com.dcorp.skyprint.core.model.TextMode
import com.dcorp.skyprint.core.model.TextStyle
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/**
 * TEST-UNIT-010 -- lệnh ESC/POS mở rộng cho nghiệp vụ POS (REQ-010's
 * req.md/design.md). Viết trước khi có code (đỏ), so byte trực tiếp với
 * lệnh ESC/POS thật, không so "trông có vẻ đúng".
 */
class PosCommandsEncoderTest {

    private fun body(vararg elements: Element, paper: PaperWidth = PaperWidth.MM80): ByteArray {
        val bytes = EscPosEncoder.encode(ReceiptDocument(paper, elements.toList()), TextMode.ASCII)
        return bytes.copyOfRange(2, bytes.size) // bỏ ESC @ init (2 byte) đầu mỗi document
    }

    @Test
    fun `text width height doc lap dung n rieng cho GS bang chi n`() {
        val bytes = body(Element.Text("x", TextStyle(width = 3, height = 5)))
        // ESC a 0 (align mặc định) rồi GS ! n -- nibble cao = height-1=4, nibble thấp = width-1=2 -> 0x42
        val expectedHead = byteArrayOf(0x1B, 0x61, 0) + byteArrayOf(0x1D, 0x21, 0x42)
        assertContentEquals(expectedHead, bytes.copyOfRange(0, 6))
    }

    @Test
    fun `text inverse bat GS B 1 truoc va tat GS B 0 sau`() {
        val bytes = body(Element.Text("HUY", TextStyle(inverse = true)))
        val expected = byteArrayOf(0x1B, 0x61, 0) +   // align left mặc định
            byteArrayOf(0x1D, 0x42, 1) +               // GS B 1 (inverse on)
            "HUY".encodeToByteArray() + byteArrayOf(0x0A) +
            byteArrayOf(0x1D, 0x42, 0)                 // GS B 0 (inverse off)
        assertContentEquals(expected, bytes)
    }

    @Test
    fun `text underline bat ESC dash 1 truoc va tat ESC dash 0 sau`() {
        val bytes = body(Element.Text("x", TextStyle(underline = true)))
        val expected = byteArrayOf(0x1B, 0x61, 0) +
            byteArrayOf(0x1B, 0x2D, 1) +
            "x".encodeToByteArray() + byteArrayOf(0x0A) +
            byteArrayOf(0x1B, 0x2D, 0)
        assertContentEquals(expected, bytes)
    }

    @Test
    fun `cut toan phan phat GS V 65 0 cut mot phan phat GS V 66 0`() {
        assertContentEquals(byteArrayOf(0x1D, 0x56, 0x41, 0x00), body(Element.Cut(partial = false)))
        assertContentEquals(byteArrayOf(0x1D, 0x56, 0x42, 0x00), body(Element.Cut(partial = true)))
    }

    @Test
    fun `drawer phat ESC p pin t1 t2`() {
        val bytes = body(Element.Drawer(pin = 0, pulseMs = 100))
        // t = (pulseMs / 2).coerceIn(1,255) = 50, đối xứng t1=t2 (DESIGN-010)
        assertContentEquals(byteArrayOf(0x1B, 0x70, 0, 50, 50), bytes)
    }

    @Test
    fun `beep ESC_B phat ESC B n t`() {
        val bytes = body(Element.Beep(times = 3, durationMs = 500))
        // n=3, t=durationMs/100=5
        assertContentEquals(byteArrayOf(0x1B, 0x42, 3, 5), bytes)
    }

    @Test
    fun `qr code van hoat dong sau refactor Cut sang data class`() {
        // hồi quy: đảm bảo sửa Element.Cut không ảnh hưởng nhánh QrCode đã pass ở REQ-001
        val bytes = body(Element.QrCode("x"), Element.Cut())
        assertContentEquals(byteArrayOf(0x1D, 0x56, 0x42, 0x00), bytes.copyOfRange(bytes.size - 4, bytes.size))
    }

    @Test
    fun `barcode CODE128 dung lenh moi GS k 73 n dulieu co tien to codeset B`() {
        val bytes = body(Element.Barcode("12345", type = BarcodeType.CODE128, heightDots = 80, hri = false, align = Align.LEFT))
        val payload = "{B12345".encodeToByteArray()
        val expected = byteArrayOf(0x1B, 0x61, 0) +          // align left
            byteArrayOf(0x1D, 0x48, 0) +                      // GS H 0 -- hri=false
            byteArrayOf(0x1D, 0x68, 80.toByte()) +            // GS h 80
            byteArrayOf(0x1D, 0x6B, 73, payload.size.toByte()) + payload
        assertContentEquals(expected, bytes)
    }

    @Test
    fun `barcode EAN13 dung lenh cu GS k 2 dulieu NUL`() {
        val bytes = body(Element.Barcode("123456789012", type = BarcodeType.EAN13, hri = true, align = Align.LEFT))
        val payload = "123456789012".encodeToByteArray()
        val expected = byteArrayOf(0x1B, 0x61, 0) +
            byteArrayOf(0x1D, 0x48, 2) +                      // GS H 2 -- hri=true (dưới mã vạch)
            byteArrayOf(0x1D, 0x68, 80.toByte()) +
            byteArrayOf(0x1D, 0x6B, 2) + payload + byteArrayOf(0x00)
        assertContentEquals(expected, bytes)
    }

    @Test
    fun `barcode EAN13 sai do dai nem INVALID_DOCUMENT ngay luc dung element`() {
        assertFailsWith<IllegalArgumentException> { Element.Barcode("123", type = BarcodeType.EAN13) }
    }
}
