package com.dcorp.skyprint.raster

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** UNIT-053-09/10: bộ dàn trang bill (căn lề, cột, xuống dòng) với bộ đo chữ giả -- không cần đồ hoạ thật. */
class ReceiptLayoutEngineTest {
    /** Chữ đơn cách: mỗi ký tự rộng nửa cỡ chữ, cao 1.25 lần cỡ chữ. */
    private object FakeMeasurer : TextMeasurer {
        override fun width(text: String, style: TextStyle) = (text.length * style.sizePx * 0.5f).toInt()
        override fun lineHeight(style: TextStyle) = (style.sizePx * 1.25f).toInt()
        override fun wrap(text: String, style: TextStyle, maxWidth: Int): List<String> {
            val per = maxOf(1, (maxWidth / (style.sizePx * 0.5f)).toInt())
            return text.chunked(per).ifEmpty { listOf("") }
        }
    }

    private val w = 512
    private val engine = ReceiptLayoutEngine(FakeMeasurer)
    private fun doc(vararg b: ReceiptBlock) = ReceiptDocument(b.toList())
    private fun ReceiptLayout2.texts() = ops.filterIsInstance<DrawOp.Text>()

    // UNIT-053-09
    @Test
    fun `chu can giua nam giua can phai sat le phai moi chu nam trong be rong anh`() {
        val l = engine.layout(doc(
            ReceiptBlock.Text("QUÁN PHỞ", ReceiptAlign.CENTER, bold = true, scale = 2),
            ReceiptBlock.Text("trái"), ReceiptBlock.Text("phải", ReceiptAlign.RIGHT),
        ), w)
        val byText = l.texts().associateBy { it.text }
        val center = byText.getValue("QUÁN PHỞ")
        val cw = FakeMeasurer.width("QUÁN PHỞ", center.style)
        assertEquals((w - cw) / 2, center.x)
        assertEquals(ReceiptLayoutEngine.PAD, byText.getValue("trái").x)
        assertEquals(w - ReceiptLayoutEngine.PAD, byText.getValue("phải").let { it.x + FakeMeasurer.width(it.text, it.style) })
        assertTrue(l.texts().all { it.x >= 0 && it.x + FakeMeasurer.width(it.text, it.style) <= w })
        assertTrue(l.texts().zipWithNext().all { (a, b) -> b.y >= a.y }, "dòng đi từ trên xuống")
        assertTrue(l.height > l.texts().last().y)
    }

    @Test
    fun `chu to dam lon hon chu thuong`() {
        val l = engine.layout(doc(ReceiptBlock.Text("a", scale = 2, bold = true), ReceiptBlock.Text("b")), w)
        val (big, small) = l.texts()
        assertTrue(big.style.sizePx > small.style.sizePx && big.style.bold && !small.style.bold)
    }

    @Test
    fun `KeyValue nhan trai gia tri phai TONG CONG to hon`() {
        val l = engine.layout(doc(ReceiptBlock.KeyValue("Bàn:", "A2"), ReceiptBlock.KeyValue("TỔNG CỘNG", "94.000", emphasis = true)), w)
        val t = l.texts()
        assertEquals(ReceiptLayoutEngine.PAD, t[0].x); assertEquals(t[0].y, t[1].y, "cùng một dòng")
        assertEquals(w - ReceiptLayoutEngine.PAD, t[1].x + FakeMeasurer.width("A2", t[1].style))
        val grand = t.filter { it.text == "TỔNG CỘNG" || it.text == "94.000" }
        assertTrue(grand.all { it.style.bold && it.style.sizePx > t[0].style.sizePx })
    }

    // UNIT-053-10
    @Test
    fun `bang mon 4 cot chia theo ti le 5-1-2-3 cot so can phai khong de len ten`() {
        val l = engine.layout(doc(ReceiptBlock.DishRow("Phở bò", "3", "50.000", "150.000")), w)
        val t = l.texts()
        val (name, qty, unit, amount) = t
        fun right(o: DrawOp.Text) = o.x + FakeMeasurer.width(o.text, o.style)
        assertEquals(w - ReceiptLayoutEngine.PAD, right(amount))
        assertTrue(right(qty) < unit.x && right(unit) < amount.x, "các cột số không chồng nhau")
        assertTrue(right(name) < qty.x - 0 || right(name) <= qty.x, "tên nằm bên trái cột SL")
        // ô tên chiếm khoảng 5/11 chiều rộng khả dụng
        val nameCol = unit.x - qty.x
        assertTrue(nameCol > 0)
        assertEquals(setOf(name.y, qty.y, unit.y, amount.y).size, 1, "cả hàng cùng dòng đầu")
    }

    @Test
    fun `ten mon dai xuong dong trong cot ten cac cot so giu o dong dau`() {
        val long = "Bún bò Huế đặc biệt nhiều giò heo thêm chả cua thật nhiều"
        val l = engine.layout(doc(ReceiptBlock.DishRow(long, "1", "50.000", "50.000")), w)
        val nameLines = l.texts().filter { long.contains(it.text) && it.text.length > 3 }
        assertTrue(nameLines.size >= 2, "tên phải xuống dòng")
        assertEquals(long, nameLines.joinToString("") { it.text })
        val firstY = nameLines.first().y
        assertTrue(l.texts().filter { it.text == "1" || it.text == "50.000" }.all { it.y == firstY })
        assertTrue(nameLines.zipWithNext().all { (a, b) -> b.y > a.y })
        assertTrue(l.texts().all { it.x >= 0 && it.x + FakeMeasurer.width(it.text, it.style) <= w })
    }

    @Test
    fun `dong phu thut le giam gia co so tien ben phai`() {
        val l = engine.layout(doc(
            ReceiptBlock.Adornment(AdornmentKind.DISCOUNT, "Giảm giá", "-5.000"),
            ReceiptBlock.Adornment(AdornmentKind.MODIFIER, "Thêm hành x2"),
        ), w)
        val t = l.texts()
        val disc = t.first { it.text.contains("Giảm giá") }; val amt = t.first { it.text == "-5.000" }; val mod = t.first { it.text.contains("Thêm hành") }
        assertTrue(disc.x > ReceiptLayoutEngine.PAD && mod.x > disc.x, "modifier thụt sâu hơn giảm giá")
        assertEquals(w - ReceiptLayoutEngine.PAD, amt.x + FakeMeasurer.width("-5.000", amt.style))
        assertTrue(!mod.style.bold)
    }

    @Test
    fun `dau gach ngang la duong ke co net dut nam trong le bang day du`() {
        val l = engine.layout(doc(ReceiptBlock.Text("a"), ReceiptBlock.Separator, ReceiptBlock.Text("b")), w)
        val line = l.ops.filterIsInstance<DrawOp.HLine>().single()
        assertEquals(ReceiptLayoutEngine.PAD, line.x1); assertEquals(w - ReceiptLayoutEngine.PAD, line.x2)
        val (a, b) = l.texts()
        assertTrue(a.y < line.y && line.y < b.y)
    }

    @Test
    fun `bill rong thi chieu cao bang 0 phan noi dung cong le va cao hon khi nhieu mon`() {
        val few = engine.layout(billOf(2), w)
        val many = engine.layout(billOf(40), w)
        assertTrue(many.height > few.height * 3, "few=${few.height} many=${many.height}")
        assertTrue(engine.layout(ReceiptDocument(emptyList()), w).height < 40)
    }

    /** Bill giả gồm tiêu đề + [n] dòng món (DishRow) + tổng, dựng tay -- không phụ thuộc mô hình đơn hàng của app. */
    private fun billOf(n: Int) = ReceiptDocument(
        listOf<ReceiptBlock>(ReceiptBlock.Text("Q", ReceiptAlign.CENTER, bold = true, scale = 2), ReceiptBlock.Separator) +
            (1..n).map { ReceiptBlock.DishRow("Món số $it", "1", "50.000", "50.000") } +
            listOf(ReceiptBlock.Separator, ReceiptBlock.KeyValue("TỔNG CỘNG", "${50 * n}.000", emphasis = true)),
    )
}
