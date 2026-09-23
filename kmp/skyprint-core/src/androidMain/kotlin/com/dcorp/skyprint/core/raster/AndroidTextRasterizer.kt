package com.dcorp.skyprint.core.raster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextPaint
import com.dcorp.skyprint.core.model.Align
import com.dcorp.skyprint.core.model.MonoBitmap
import kotlin.math.roundToInt

/**
 * Vẽ chữ thành ảnh 1-bit bằng `Canvas`/`TextPaint` của Android (REQ-002,
 * DESIGN-002). Test bằng Robolectric (`androidUnitTest`), không cần thiết
 * bị/emulator thật -- Robolectric ≥4.4 dựng lại Canvas/Paint/Bitmap bằng
 * Skia native thật (không phải stub trả 0), nên `measureText`/`drawText`
 * cho kết quả gần đúng như trên máy thật.
 *
 * DÙNG CHUNG MỘT FONT ĐƠN CÁCH (monospace) cho MỌI dòng, kể cả [Element.Text]
 * tự do -- không chỉ [Element.Row]/[Element.Divider] (vốn đã canh cột sẵn
 * bằng khoảng trắng ở [com.dcorp.skyprint.core.encode.LayoutEngine], xem
 * [RasterLine]'s doc). Đánh đổi thẩm mỹ (chữ tự do trông "đơn điệu" hơn
 * font tỷ lệ) lấy sự chắc chắn: nếu Text và Row dùng font khác nhau, cột
 * của Row sẽ lệch bất cứ khi nào layout đổi độ rộng do font tỷ lệ đo khác
 * font đơn cách. Đổi sang font tỷ lệ riêng cho Text cần thêm cờ phân biệt
 * "dòng đã canh cột sẵn hay chưa" vào [RasterLine] -- để sau nếu cần, không
 * đoán trước.
 */
class AndroidTextRasterizer(
    private val baseTextSizePx: Float = 32f,
    private val typeface: Typeface = Typeface.MONOSPACE,
    /** Ngưỡng xám->đen/trắng cho [Dither.threshold] sau khi vẽ (0..255). */
    private val ditherLevel: Int = 200,
) : TextRasterizer {

    override fun render(block: RasterBlock, widthDots: Int): MonoBitmap {
        val safeWidth = widthDots.coerceAtLeast(1)
        val linePaints = block.lines.map { line -> line to buildPaints(line) }
        val lineHeights = linePaints.map { (_, paints) -> lineHeightOf(paints) }
        val totalHeight = lineHeights.sum().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(safeWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = 0f
        for (index in block.lines.indices) {
            val (line, paints) = linePaints[index]
            val height = lineHeights[index]
            drawLine(canvas, line, paints, safeWidth, y, height.toFloat())
            y += height
        }

        return toMonoBitmap(bitmap, ditherLevel)
    }

    private fun buildPaints(line: RasterLine): List<TextPaint> = line.spans.map { span ->
        TextPaint().apply {
            isAntiAlias = true
            this.typeface = this@AndroidTextRasterizer.typeface
            textSize = baseTextSizePx * span.heightScale.coerceAtLeast(1)
            textScaleX = span.widthScale.coerceAtLeast(1).toFloat()
            isFakeBoldText = span.bold
            isUnderlineText = span.underline
            color = Color.BLACK
        }
    }

    private fun lineHeightOf(paints: List<TextPaint>): Int {
        if (paints.isEmpty()) return baseTextSizePx.roundToInt()
        return paints.maxOf { paint ->
            val metrics = paint.fontMetrics
            (metrics.bottom - metrics.top).roundToInt()
        }
    }

    private fun drawLine(canvas: Canvas, line: RasterLine, paints: List<TextPaint>, widthDots: Int, top: Float, height: Float) {
        if (line.spans.isEmpty()) return

        val spanWidths = line.spans.mapIndexed { i, span -> paints[i].measureText(span.text) }
        val totalWidth = spanWidths.sum()
        var x = when (line.align) {
            Align.LEFT -> 0f
            Align.CENTER -> ((widthDots - totalWidth) / 2f).coerceAtLeast(0f)
            Align.RIGHT -> (widthDots - totalWidth).coerceAtLeast(0f)
        }

        for (i in line.spans.indices) {
            val span = line.spans[i]
            val paint = paints[i]
            val spanWidth = spanWidths[i]
            val baseline = top - paint.fontMetrics.top

            if (span.inverse) {
                val fillPaint = TextPaint(paint).apply { color = Color.BLACK; style = android.graphics.Paint.Style.FILL }
                canvas.drawRect(x, top, x + spanWidth, top + height, fillPaint)
                paint.color = Color.WHITE
            }
            canvas.drawText(span.text, x, baseline, paint)
            if (span.inverse) paint.color = Color.BLACK // trả lại cho lần dùng sau (mỗi span dùng 1 lần nhưng an toàn nếu tái dùng)

            x += spanWidth
        }
    }

    private fun toMonoBitmap(bitmap: Bitmap, level: Int): MonoBitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = ByteArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Trọng số luma chuẩn (Rec. 601) -- đủ cho mục đích ngưỡng đen/trắng, không cần chính xác màu.
            gray[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
        }
        return Dither.threshold(gray, width, height, level)
    }
}
