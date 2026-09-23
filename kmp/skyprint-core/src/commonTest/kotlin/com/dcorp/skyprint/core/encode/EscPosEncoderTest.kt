package com.dcorp.skyprint.core.encode

import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import com.dcorp.skyprint.core.model.Align
import com.dcorp.skyprint.core.model.Column
import com.dcorp.skyprint.core.model.Element
import com.dcorp.skyprint.core.model.PaperWidth
import com.dcorp.skyprint.core.model.QrErrorCorrection
import com.dcorp.skyprint.core.model.ReceiptDocument
import com.dcorp.skyprint.core.model.TextMode
import com.dcorp.skyprint.core.model.TextStyle
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * TEST-UNIT-001 -- viết trước khi có [EscPosEncoder] (đỏ), theo thứ tự TDD
 * của WORKFLOW.md. Mọi so sánh byte đối chiếu trực tiếp với lệnh ESC/POS
 * thật (không so bằng "trông có vẻ đúng"), khớp acceptance criteria của
 * docs/features/REQ-001/req.md.
 */
class EscPosEncoderTest {

    private fun encode(vararg elements: Element, paper: PaperWidth = PaperWidth.MM80): ByteArray =
        EscPosEncoder.encode(ReceiptDocument(paper, elements.toList()), TextMode.ASCII)

    @Test
    fun `moi hoa don bat dau bang ESC at reset`() {
        val bytes = encode(Element.Text("x"))
        assertContentEquals(byteArrayOf(0x1B, 0x40), bytes.copyOfRange(0, 2))
    }

    @Test
    fun `text don gian phat dung lenh align bold size roi text roi line feed`() {
        val bytes = encode(
            Element.Text("SKYPOS", TextStyle(align = Align.CENTER, bold = true, width = 2, height = 2)),
        )
        val expectedTail = byteArrayOf(0x1B, 0x61, 1) +      // ESC a 1 (center)
            byteArrayOf(0x1B, 0x45, 1) +                     // ESC E 1 (bold on)
            byteArrayOf(0x1D, 0x21, 0x11) +                  // GS ! 0x11 (double w+h, size=2)
            "SKYPOS".encodeToByteArray() +
            byteArrayOf(0x0A) +
            byteArrayOf(0x1D, 0x21, 0x00) +                  // size trả về bình thường -- tắt theo thứ tự ngược lúc bật (LIFO)
            byteArrayOf(0x1B, 0x45, 0)                       // bold tắt lại sau cùng
        assertContentEquals(expectedTail, bytes.copyOfRange(2, bytes.size))
    }

    @Test
    fun `text bo dau tieng Viet o che do ASCII`() {
        val bytes = encode(Element.Text("Phở bò tái – Cảm ơn quý khách! ỹ"))
        val text = bytes.decodeToString()
        assertTrue(text.contains("Pho bo tai"), "phải bỏ dấu, thực tế: $text")
        assertTrue(text.contains("Cam on quy khach"), "phải bỏ dấu, thực tế: $text")
    }

    @Test
    fun `ky tu khong map duoc thay bang dau hoi`() {
        val bytes = encode(Element.Text("emoji 😀 end"))
        val text = bytes.decodeToString()
        assertTrue(text.contains("emoji ? end") || text.contains("emoji ?? end"), "thực tế: $text")
    }

    @Test
    fun `row can phai gia tri khong bao gio bi cat`() {
        val bytes = encode(
            Element.Row(
                listOf(
                    Column("Ten mon rat rat rat dai qua khong vua dong", weight = 6),
                    Column("123.456", weight = 3, align = Align.RIGHT),
                ),
            ),
            paper = PaperWidth.MM58, // 32 cột
        )
        val line = bytes.copyOfRange(2, bytes.size).decodeToString().lines().first { it.contains("123.456") }
        assertTrue(line.endsWith("123.456"), "value phải luôn nằm trọn ở cuối dòng, thực tế: '$line'")
        assertEquals(32, line.length, "dòng phải khớp đúng bề rộng khổ giấy")
    }

    @Test
    fun `divider lap dung ky tu theo be rong kho giay`() {
        val bytes = encode(Element.Divider(), paper = PaperWidth.MM58)
        val line = bytes.copyOfRange(2, bytes.size).decodeToString().trimEnd('\n')
        assertEquals("-".repeat(32), line)
    }

    @Test
    fun `feed am duoc clamp ve 0`() {
        val bytes = encode(Element.Feed(-5))
        assertContentEquals(byteArrayOf(0x1B, 0x64, 0), bytes.copyOfRange(2, bytes.size))
    }

    @Test
    fun `feed vuot 255 duoc clamp ve 255`() {
        val bytes = encode(Element.Feed(999))
        assertContentEquals(byteArrayOf(0x1B, 0x64, 0xFF.toByte()), bytes.copyOfRange(2, bytes.size))
    }

    @Test
    fun `cut phat dung lenh GS V 66 0`() {
        val bytes = encode(Element.Cut())
        assertContentEquals(byteArrayOf(0x1D, 0x56, 0x42, 0x00), bytes.copyOfRange(2, bytes.size))
    }

    @Test
    fun `qr code phat dung chuoi lenh GS parenleft k theo chuan Epson`() {
        val bytes = encode(
            Element.QrCode("hello", moduleSize = 5, errorCorrection = QrErrorCorrection.M, align = Align.LEFT),
        )
        val body = bytes.copyOfRange(2, bytes.size)

        // 0) căn lề theo Align.LEFT trước khi in QR (ESC a cũng áp dụng cho GS ( k fn=81)
        val alignLeft = byteArrayOf(0x1B, 0x61, 0)
        // 1) chọn model 2: GS ( k 04 00 31 41 32 00
        val selectModel = byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00)
        // 2) module size: GS ( k 03 00 31 43 <n>
        val setSize = byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 5)
        // 3) error correction: GS ( k 03 00 31 45 <48+level>  (M=49)
        val setEcc = byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 49)
        // 4) store data: GS ( k pL pH 31 50 30 <data>  (pL/pH = len(data)+3, little-endian)
        val data = "hello".encodeToByteArray()
        val storeLen = data.size + 3
        val storeData = byteArrayOf(0x1D, 0x28, 0x6B, (storeLen % 256).toByte(), (storeLen / 256).toByte(), 0x31, 0x50, 0x30) + data
        // 5) print: GS ( k 03 00 31 51 30
        val print = byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30)

        val expected = alignLeft + selectModel + setSize + setEcc + storeData + print
        assertContentEquals(expected, body)
    }

    @Test
    fun `cau truc phan tu sai nem loi ngay luc dung document khong doi den encode`() {
        // Column.weight <= 0 và Row rỗng là lỗi cấu trúc phát hiện được ngay lúc dựng
        // model (fail-fast, DESIGN-001) -- không đợi tới lúc gọi encode mới báo.
        assertFailsWith<IllegalArgumentException> { Element.Row(emptyList()) }
        assertFailsWith<IllegalArgumentException> { Column("x", weight = 0) }
    }

    @Test
    fun `mode RASTER khi chua co rasterizer nem UNSUPPORTED_PLATFORM khong phat byte nao`() {
        val ex = assertFailsWith<PrinterException> {
            EscPosEncoder.encode(
                ReceiptDocument(PaperWidth.MM58, listOf(Element.Text("x"))),
                TextMode.RASTER,
            )
        }
        assertEquals(PrinterErrorCode.UNSUPPORTED_PLATFORM, ex.code)
    }
}
