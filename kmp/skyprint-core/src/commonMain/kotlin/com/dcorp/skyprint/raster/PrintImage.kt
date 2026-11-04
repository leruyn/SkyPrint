package com.dcorp.skyprint.raster

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Tông màu khi đổi ảnh màu/xám sang đen trắng cho đầu in nhiệt. Mặc định [Thermal] khớp `PrinterHelper.optimizeBitmapForPrinter`
 * của skytab-android (contrast 1,18, ngưỡng 152) -- đã chạy ổn trên máy in thật.
 *
 * [MonoConverter] cắt ở ngưỡng 128 cố định, nên [threshold] khác 128 được quy về bằng cách dời mức xám đi `threshold - 128`.
 */
data class PrintTone(val contrast: Float = 1.18f, val threshold: Int = 152) {
    companion object {
        val Thermal = PrintTone()

        /** Không chỉnh gì: giữ nguyên mức xám của ảnh nguồn. */
        val Neutral = PrintTone(contrast = 1f, threshold = 128)
    }
}

/**
 * Ảnh dựng sẵn từ app (Compose, Canvas, view chụp...) → [GrayBitmap] đúng bề rộng máy in, sẵn cho [MonoConverter] và
 * [EscPosRaster]. Thuần commonMain nên cùng một kết quả trên Android/iOS/JVM, và test được không cần thiết bị.
 *
 * - Điểm trong suốt được ghép lên nền trắng (ảnh chụp từ layer không có nền giấy).
 * - Ảnh đã đúng [targetWidth] được giữ nguyên từng điểm, KHÔNG lấy mẫu lại -- nét chữ vẽ ở kích thước thật không bị mờ.
 *   Nên dựng ảnh ngay ở [com.dcorp.skyprint.core.model.PaperWidth.contentDots], đừng chụp màn hình rồi co.
 * - Khác bề rộng (dự phòng) thì co/giãn bằng trung bình theo vùng, giữ tỉ lệ.
 */
object PrintImage {
    /**
     * [argb]: `width * height` điểm ARGB_8888 theo hàng từ trên xuống, alpha THẲNG (không nhân trước) -- đúng định dạng của
     * `Bitmap.getPixels` trên Android.
     */
    fun toGray(argb: IntArray, width: Int, height: Int, targetWidth: Int = width, tone: PrintTone = PrintTone.Thermal): GrayBitmap {
        require(width > 0 && height > 0) { "Ảnh rỗng: ${width}x$height" }
        require(argb.size == width * height) { "argb ${argb.size} != ${width}x$height" }
        require(targetWidth > 0) { "targetWidth phải > 0" }

        val src = IntArray(argb.size) { i ->
            val c = argb[i]
            val a = ((c ushr 24) and 0xFF) / 255f
            val lum = 0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
            (lum * a + 255f * (1f - a)).roundToInt().coerceIn(0, 255)
        }

        val outW = targetWidth
        val outH: Int
        val gray: IntArray
        if (width == outW) {
            outH = height
            gray = src
        } else {
            val scale = outW.toFloat() / width
            outH = (height * scale).roundToInt().coerceAtLeast(1)
            gray = IntArray(outW * outH)
            for (dy in 0 until outH) {
                val y0 = floor(dy / scale).toInt().coerceIn(0, height - 1)
                val y1 = ceil((dy + 1) / scale).toInt().coerceIn(y0 + 1, height)
                for (dx in 0 until outW) {
                    val x0 = floor(dx / scale).toInt().coerceIn(0, width - 1)
                    val x1 = ceil((dx + 1) / scale).toInt().coerceIn(x0 + 1, width)
                    var sum = 0L
                    for (y in y0 until y1) {
                        val row = y * width
                        for (x in x0 until x1) sum += src[row + x]
                    }
                    gray[dy * outW + dx] = (sum / ((y1 - y0) * (x1 - x0))).toInt()
                }
            }
        }

        val shift = tone.threshold - 128
        val out = ByteArray(outW * outH)
        for (i in out.indices) {
            out[i] = ((gray[i] - 128) * tone.contrast + 128f - shift).roundToInt().coerceIn(0, 255).toByte()
        }
        return GrayBitmap(outW, outH, out)
    }
}
