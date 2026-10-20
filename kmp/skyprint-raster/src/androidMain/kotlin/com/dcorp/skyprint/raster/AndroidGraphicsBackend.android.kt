package com.dcorp.skyprint.raster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import java.util.TimeZone
import kotlin.math.ceil

/**
 * Bộ vẽ bill bằng Android Canvas (REQ-053). Font sans-serif của hệ thống (Roboto) có dấu tiếng Việt; cỡ chữ lấy từ
 * [TextStyle.sizePx] là điểm ảnh thật nên không phụ thuộc mật độ màn hình.
 */
class AndroidGraphicsBackend : GraphicsBackend {
    private fun paint(style: TextStyle) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, if (style.bold) Typeface.BOLD else Typeface.NORMAL)
        textSize = style.sizePx
        color = Color.BLACK
    }

    override val measurer = object : TextMeasurer {
        override fun width(text: String, style: TextStyle) = ceil(paint(style).measureText(text)).toInt()
        override fun lineHeight(style: TextStyle): Int {
            val fm = paint(style).fontMetrics
            return ceil(fm.descent - fm.ascent).toInt()
        }
        override fun wrap(text: String, style: TextStyle, maxWidth: Int) = TextWrap.wrap(text, maxWidth) { width(it, style) }
    }

    override fun createSurface(width: Int, height: Int): DrawSurface = object : DrawSurface {
        private val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        private val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }

        override fun drawText(text: String, x: Int, y: Int, style: TextStyle) {
            val p = paint(style)
            canvas.drawText(text, x.toFloat(), y - p.fontMetrics.ascent, p)
        }

        override fun drawHLine(y: Int, x1: Int, x2: Int, thickness: Int, dashed: Boolean) {
            val p = Paint().apply {
                color = Color.BLACK; strokeWidth = thickness.toFloat(); style = Paint.Style.STROKE
                if (dashed) pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
            }
            canvas.drawLine(x1.toFloat(), y.toFloat(), x2.toFloat(), y.toFloat(), p)
        }

        override fun toGray(): GrayBitmap {
            val argb = IntArray(width * height)
            bitmap.getPixels(argb, 0, width, 0, 0, width, height)
            bitmap.recycle()
            val gray = ByteArray(width * height) {
                val c = argb[it]
                ((Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000).toByte()
            }
            return GrayBitmap(width, height, gray)
        }
    }
}

/** Bill raster của Android Master: dựng bằng Canvas, giờ in theo múi giờ của máy. */
fun createPlatformReceiptRasterizer(): ReceiptRasterizer = GraphicsReceiptRasterizer(AndroidGraphicsBackend())

/** Độ lệch múi giờ hiện tại của máy (phút) tại [epochMs] -- để in giờ địa phương. */
fun platformUtcOffsetMinutes(epochMs: Long): Int = TimeZone.getDefault().getOffset(epochMs) / 60_000
