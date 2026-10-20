package com.dcorp.skyprint.raster

enum class ReceiptAlign { LEFT, CENTER, RIGHT }

enum class AdornmentKind { DISCOUNT, MODIFIER, COMPONENT }

/** Một khối của bill. Thuần dữ liệu: bộ vẽ theo nền tảng ([ReceiptRasterizer]) quyết định font/kích thước điểm ảnh. */
sealed interface ReceiptBlock {
    /** [scale] 1 = chữ thường, 2 = chữ to (tên quán). */
    data class Text(val text: String, val align: ReceiptAlign = ReceiptAlign.LEFT, val bold: Boolean = false, val scale: Int = 1) : ReceiptBlock
    data object Separator : ReceiptBlock
    /** Nhãn trái, giá trị phải; [emphasis] = TỔNG CỘNG (đậm, to hơn). */
    data class KeyValue(val label: String, val value: String, val emphasis: Boolean = false) : ReceiptBlock
    /** Hàng nhiều cột chia theo [weights] (tiêu đề bảng món); ô đầu căn trái, các ô sau căn phải. */
    class Columns(val cells: List<String>, val weights: FloatArray, val bold: Boolean = false) : ReceiptBlock
    data class DishRow(val name: String, val qty: String, val unit: String, val amount: String) : ReceiptBlock
    /** Dòng phụ dưới món: giảm giá (kèm [amount]), modifier, thành phần combo. */
    data class Adornment(val kind: AdornmentKind, val text: String, val amount: String? = null) : ReceiptBlock
    data object Spacer : ReceiptBlock
}

class ReceiptDocument(val blocks: List<ReceiptBlock>)

/** Tỉ lệ cột bảng món `Tên : SL : Đ.Giá : T.Tiền` -- đúng như `PreCheckPreviewRenderer` của skytab-android. */
object ReceiptLayout {
    val DISH_WEIGHTS: FloatArray = floatArrayOf(5f, 1f, 2f, 3f)
}
