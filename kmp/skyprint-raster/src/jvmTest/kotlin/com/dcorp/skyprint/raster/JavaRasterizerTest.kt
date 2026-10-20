package com.dcorp.skyprint.raster

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Bản JVM của SIT-053-01/02: Java2D -> ảnh đúng bề rộng, không rỗng, dấu tiếng Việt được vẽ. Ghi PNG vào build/receipt-samples để xem bằng mắt. */
class JavaRasterizerTest {
    private val rasterizer = createPlatformReceiptRasterizer()

    private fun bill() = ReceiptDocument(
        listOf(
            ReceiptBlock.Text("Quán Phở Hà Nội", ReceiptAlign.CENTER, bold = true, scale = 2),
            ReceiptBlock.Text("12 Lê Lợi, Quận 1", ReceiptAlign.CENTER),
            ReceiptBlock.Separator,
            ReceiptBlock.KeyValue("Bàn:", "A2"),
            ReceiptBlock.Columns(listOf("Tên hàng", "SL", "Đ.Giá", "T.Tiền"), ReceiptLayout.DISH_WEIGHTS, bold = true),
            ReceiptBlock.DishRow("Bún bò Huế đặc biệt", "2", "65.000", "130.000"),
            ReceiptBlock.Adornment(AdornmentKind.MODIFIER, "Thêm chả cua"),
            ReceiptBlock.DishRow("Phở bò tái nạm gầu gân sách nhiều hành", "1", "55.000", "49.500"),
            ReceiptBlock.Adornment(AdornmentKind.DISCOUNT, "Giảm giá", "-5.500"),
            ReceiptBlock.DishRow("Trà đá", "3", "5.000", "15.000"),
            ReceiptBlock.Separator,
            ReceiptBlock.KeyValue("TỔNG CỘNG", "194.500", emphasis = true),
            ReceiptBlock.Spacer,
            ReceiptBlock.Text("Cảm ơn quý khách!", ReceiptAlign.CENTER),
        ),
    )

    private fun png(name: String, gray: GrayBitmap) {
        val img = BufferedImage(gray.width, gray.height, BufferedImage.TYPE_BYTE_GRAY)
        for (y in 0 until gray.height) for (x in 0 until gray.width) img.raster.setSample(x, y, 0, gray.pixels[y * gray.width + x].toInt() and 0xFF)
        val dir = File("build/receipt-samples").apply { mkdirs() }
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    @Test
    fun `ve bill dung be rong 360 va 512, khong rong, le phai trang`() {
        for (width in listOf(360, 512)) {
            val gray = rasterizer.render(bill(), width)
            assertEquals(width, gray.width)
            assertTrue(gray.height > 300, "height=${gray.height}")
            val dark = gray.pixels.count { (it.toInt() and 0xFF) < 100 }
            assertTrue(dark > gray.pixels.size / 100, "có chữ: dark=$dark")
            assertTrue(dark < gray.pixels.size / 3, "không đen kín")
            png("bill-$width", gray)
            val edge = (0 until gray.height).count { y -> (gray.pixels[y * gray.width + gray.width - 1].toInt() and 0xFF) < 100 }
            assertEquals(0, edge, "cột điểm cuối cùng phải trắng")
        }
    }

    @Test
    fun `dau tieng Viet duoc ve - Bun co dau khac Bun khong dau`() {
        fun render(t: String) = rasterizer.render(ReceiptDocument(listOf(ReceiptBlock.Text(t))), 512).pixels
        assertNotEquals(render("Bún bò Huế").toList(), render("Bun bo Hue").toList())
    }
}
