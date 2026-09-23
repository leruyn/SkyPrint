package com.dcorp.skyprint.core.raster

import com.dcorp.skyprint.core.model.MonoBitmap

/**
 * Chuyển ảnh xám (grayscale, 1 byte/pixel, 0=đen 255=trắng) thành [MonoBitmap]
 * 1-bit bằng ngưỡng đơn giản (REQ-002). Không dùng Floyd-Steinberg hay
 * thuật toán dither nâng cao ở v1 -- text render ra glyph có cạnh rõ, không
 * cần khử răng cưa bằng dither; chỉ ảnh logo mới cần, để sau khi có nhu cầu
 * thật (không đoán trước).
 */
object Dither {
    fun threshold(gray: ByteArray, width: Int, height: Int, level: Int = 128): MonoBitmap {
        require(gray.size == width * height) {
            "gray.size phải bằng width*height ($width*$height=${width * height}), nhận ${gray.size}"
        }
        val rowBytes = (width + 7) / 8
        val bits = ByteArray(rowBytes * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = gray[y * width + x].toInt() and 0xFF
                if (pixel < level) {
                    val byteIndex = y * rowBytes + x / 8
                    val bitMask = 1 shl (7 - (x % 8))
                    bits[byteIndex] = (bits[byteIndex].toInt() or bitMask).toByte()
                }
            }
        }
        return MonoBitmap(width, height, bits)
    }
}
