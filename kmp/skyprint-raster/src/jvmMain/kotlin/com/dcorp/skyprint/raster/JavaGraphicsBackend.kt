package com.dcorp.skyprint.raster

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Bộ vẽ bill bằng Java2D (Desktop JVM và test trên máy dev). Font "SansSerif" của JVM có dấu tiếng Việt;
 * cỡ chữ lấy từ [TextStyle.sizePx] (điểm ảnh) nên bill ra đúng bề rộng đầu in.
 */
class JavaGraphicsBackend : GraphicsBackend {
    private fun font(style: TextStyle) = Font(Font.SANS_SERIF, if (style.bold) Font.BOLD else Font.PLAIN, 1).deriveFont(style.sizePx)

    // Graphics2D để đo chữ (không cần bề mặt thật)
    private val scratch = BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY).createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    }

    override val measurer = object : TextMeasurer {
        override fun width(text: String, style: TextStyle) = scratch.getFontMetrics(font(style)).stringWidth(text)
        override fun lineHeight(style: TextStyle) = scratch.getFontMetrics(font(style)).height
        override fun wrap(text: String, style: TextStyle, maxWidth: Int) = TextWrap.wrap(text, maxWidth) { width(it, style) }
    }

    override fun createSurface(width: Int, height: Int): DrawSurface = object : DrawSurface {
        private val img = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        private val g = img.createGraphics().apply {
            color = Color.WHITE; fillRect(0, 0, width, height); color = Color.BLACK
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        }

        override fun drawText(text: String, x: Int, y: Int, style: TextStyle) {
            g.font = font(style)
            g.drawString(text, x, y + g.fontMetrics.ascent)
        }

        override fun drawHLine(y: Int, x1: Int, x2: Int, thickness: Int, dashed: Boolean) {
            g.stroke = if (dashed) BasicStroke(thickness.toFloat(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(6f, 4f), 0f)
            else BasicStroke(thickness.toFloat())
            g.drawLine(x1, y, x2, y)
        }

        override fun toGray(): GrayBitmap {
            g.dispose()
            val px = ByteArray(width * height)
            val raster = img.raster
            for (yy in 0 until height) for (xx in 0 until width) px[yy * width + xx] = raster.getSample(xx, yy, 0).toByte()
            return GrayBitmap(width, height, px)
        }
    }
}

/** Desktop/Windows: bộ vẽ Java2D (có dấu tiếng Việt). */
fun createPlatformReceiptRasterizer(): ReceiptRasterizer = GraphicsReceiptRasterizer(JavaGraphicsBackend())
