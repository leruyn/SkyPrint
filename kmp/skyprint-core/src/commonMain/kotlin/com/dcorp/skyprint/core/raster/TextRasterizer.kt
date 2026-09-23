package com.dcorp.skyprint.core.raster

import com.dcorp.skyprint.core.model.Align
import com.dcorp.skyprint.core.model.MonoBitmap

/**
 * Một đoạn chữ cùng kiểu trong [RasterLine] (REQ-002). [widthScale]/[heightScale]
 * khớp [com.dcorp.skyprint.core.model.TextStyle.width]/`.height` (1..8).
 */
data class RasterSpan(
    val text: String,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
    val widthScale: Int = 1,
    val heightScale: Int = 1,
)

/**
 * [Element.Row]/[Element.Divider] được [EscPosEncoder] quy về MỘT [RasterSpan]
 * đã canh cột sẵn bằng [com.dcorp.skyprint.core.encode.LayoutEngine] (tái
 * dùng logic ASCII, không viết lại) -- vì vậy [TextRasterizer] cần vẽ bằng
 * font đơn cách (monospace) cho các dòng này để giữ thẳng cột; [Element.Text]
 * thì không bắt buộc.
 */
data class RasterLine(val spans: List<RasterSpan>, val align: Align = Align.LEFT)

data class RasterBlock(val lines: List<RasterLine>)

/**
 * Vẽ chữ thành ảnh 1-bit theo nền tảng (REQ-002) -- Android dùng `Canvas`/
 * `StaticLayout`, iOS dùng `CoreText`, Flutter dùng `dart:ui`. CHƯA cài ở
 * CODE-002 (v1 mới có contract + [EscPosEncoder] nối vào qua interface này,
 * đã kiểm bằng rasterizer giả trong test) -- 3 cài đặt thật cần toolchain
 * Android/iOS riêng, theo dõi tiếp ở CODE-002b/CODE-002c.
 */
interface TextRasterizer {
    fun render(block: RasterBlock, widthDots: Int): MonoBitmap
}
