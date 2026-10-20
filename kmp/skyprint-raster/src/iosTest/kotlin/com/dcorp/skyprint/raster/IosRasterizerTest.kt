package com.dcorp.skyprint.raster

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Bản iOS Simulator của SIT-053-03: CoreGraphics -> đúng bề rộng, không rỗng, dấu tiếng Việt được vẽ. */
class IosRasterizerTest {
    private val rasterizer = createPlatformReceiptRasterizer()

    private val doc = ReceiptDocument(
        listOf(
            ReceiptBlock.Text("Quán Phở", ReceiptAlign.CENTER, bold = true, scale = 2),
            ReceiptBlock.Separator,
            ReceiptBlock.DishRow("Bún bò Huế đặc biệt", "2", "65.000", "130.000"),
            ReceiptBlock.KeyValue("TỔNG CỘNG", "130.000", emphasis = true),
        ),
    )

    @Test
    fun bill_dung_be_rong_va_co_chu() {
        for (width in listOf(360, 512)) {
            val gray = rasterizer.render(doc, width)
            assertEquals(width, gray.width)
            assertTrue(gray.height > 100, "height=${gray.height}")
            val dark = gray.pixels.count { (it.toInt() and 0xFF) < 100 }
            assertTrue(dark > gray.pixels.size / 100, "có chữ: dark=$dark")
        }
    }

    @Test
    fun dau_tieng_viet_duoc_ve() {
        fun render(t: String) = rasterizer.render(ReceiptDocument(listOf(ReceiptBlock.Text(t))), 512).pixels
        assertNotEquals(render("Bún bò Huế").toList(), render("Bun bo Hue").toList())
    }
}
