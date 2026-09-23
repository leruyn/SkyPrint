package com.dcorp.skyprint.core.encode

import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import com.dcorp.skyprint.core.model.Align
import com.dcorp.skyprint.core.model.Column
import com.dcorp.skyprint.core.model.Element
import com.dcorp.skyprint.core.model.MonoBitmap
import com.dcorp.skyprint.core.model.PaperWidth
import com.dcorp.skyprint.core.model.ReceiptDocument
import com.dcorp.skyprint.core.model.TextMode
import com.dcorp.skyprint.core.model.TextStyle
import com.dcorp.skyprint.core.raster.RasterBlock
import com.dcorp.skyprint.core.raster.RasterPacker
import com.dcorp.skyprint.core.raster.TextRasterizer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * TEST-UNIT-002 (bổ sung) -- nối [EscPosEncoder] với [TextRasterizer]
 * (REQ-002/DESIGN-002). Rasterizer giả (1 dòng = 1 dot cao, đủ để kiểm
 * việc gom nhóm/đóng gói mà không cần font/canvas thật) -- rasterizer thật
 * (Android Canvas/iOS CoreText/Flutter dart:ui) chưa cài, xem CHANGELOG.
 */
class RasterEncoderTest {

    /** Trả bitmap 1xN toàn bit 1 (N = số dòng nhận được) -- chỉ để kiểm SỐ DÒNG mỗi lần gọi, không kiểm nội dung glyph thật. */
    private class CountingRasterizer(val calls: MutableList<RasterBlock> = mutableListOf()) : TextRasterizer {
        override fun render(block: RasterBlock, widthDots: Int): MonoBitmap {
            calls += block
            return MonoBitmap(width = 8, height = block.lines.size, bits = ByteArray(block.lines.size) { 0xFF.toByte() })
        }
    }

    private fun encode(vararg elements: Element, rasterizer: TextRasterizer): ByteArray =
        EscPosEncoder.encode(ReceiptDocument(PaperWidth.MM58, elements.toList()), TextMode.RASTER, rasterizer = rasterizer)

    @Test
    fun `khong co rasterizer van nem UNSUPPORTED_PLATFORM nhu truoc`() {
        val ex = assertFailsWith<PrinterException> {
            EscPosEncoder.encode(ReceiptDocument(PaperWidth.MM58, listOf(Element.Text("x"))), TextMode.RASTER)
        }
        assertEquals(PrinterErrorCode.UNSUPPORTED_PLATFORM, ex.code)
    }

    @Test
    fun `text lien tiep gom thanh 1 lan goi rasterizer roi dong goi GS v 0`() {
        val rasterizer = CountingRasterizer()
        val bytes = encode(Element.Text("dòng 1"), Element.Text("dòng 2"), rasterizer = rasterizer)

        assertEquals(1, rasterizer.calls.size, "2 Text liên tiếp phải gộp thành 1 lần gọi rasterizer, không phải 2")
        assertEquals(2, rasterizer.calls.single().lines.size)

        val expectedBitmap = MonoBitmap(width = 8, height = 2, bits = byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
        val body = bytes.copyOfRange(2, bytes.size) // bỏ ESC @ init
        assertContentEquals(RasterPacker.toGsV0(expectedBitmap), body)
    }

    @Test
    fun `phan tu khac text ngat nhom raster thanh nhieu lan goi`() {
        val rasterizer = CountingRasterizer()
        encode(Element.Text("a"), Element.Cut(), Element.Text("b"), rasterizer = rasterizer)

        assertEquals(2, rasterizer.calls.size, "Cut ở giữa phải ngắt nhóm raster thành 2 lần gọi riêng")
    }

    @Test
    fun `row va divider cung duoc gom vao raster block dung canh cot ASCII`() {
        val rasterizer = CountingRasterizer()
        encode(
            Element.Row(listOf(Column("Phở", weight = 6), Column("50.000", weight = 3, align = Align.RIGHT))),
            Element.Divider(),
            rasterizer = rasterizer,
        )
        val block = rasterizer.calls.single()
        assertEquals(2, block.lines.size)
        val rowText = block.lines[0].spans.single().text
        assertEquals(32, rowText.length, "Row raster phải dùng đúng layout ASCII (LayoutEngine) để giữ thẳng cột")
        assertEquals(true, rowText.endsWith("50.000"))
        assertEquals("-".repeat(32), block.lines[1].spans.single().text)
    }

    @Test
    fun `text style duoc truyen dung sang RasterSpan`() {
        val rasterizer = CountingRasterizer()
        encode(Element.Text("x", TextStyle(bold = true, width = 2, height = 3, align = Align.CENTER)), rasterizer = rasterizer)

        val span = rasterizer.calls.single().lines.single().spans.single()
        assertEquals(true, span.bold)
        assertEquals(2, span.widthScale)
        assertEquals(3, span.heightScale)
        assertEquals(Align.CENTER, rasterizer.calls.single().lines.single().align)
    }

    @Test
    fun `element Image luon dung RasterPacker bat ke mode ASCII hay RASTER`() {
        val bitmap = MonoBitmap(width = 8, height = 1, bits = byteArrayOf(0x0F))
        val docAscii = ReceiptDocument(PaperWidth.MM58, listOf(Element.Image(bitmap)))
        val bytesAscii = EscPosEncoder.encode(docAscii, TextMode.ASCII)

        val expected = byteArrayOf(0x1B, 0x61, 1) + RasterPacker.toGsV0(bitmap) // align CENTER mặc định của Image
        assertContentEquals(expected, bytesAscii.copyOfRange(2, bytesAscii.size))
    }
}
