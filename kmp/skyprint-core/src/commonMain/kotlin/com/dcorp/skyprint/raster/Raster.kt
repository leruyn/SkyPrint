package com.dcorp.skyprint.raster

/** Ảnh thang xám 8-bit, mỗi byte một điểm (0 = đen, 255 = trắng), theo hàng từ trên xuống. */
class GrayBitmap(val width: Int, val height: Int, val pixels: ByteArray) {
    init { require(pixels.size == width * height) { "pixels ${pixels.size} != ${width}x$height" } }
}

/** Ảnh 1bpp: bit 1 = chấm đen, MSB là điểm trái nhất; mỗi hàng [rowBytes] byte, phần đệm bên phải là trắng (0). */
class MonoBitmap(val width: Int, val height: Int, val data: ByteArray) {
    val rowBytes: Int get() = (width + 7) / 8
}

/** Bộ vẽ bill thành ảnh -- mỗi nền tảng một bản (Android: Canvas, iOS: CoreGraphics); `null` nếu nền tảng chưa có. */
fun interface ReceiptRasterizer {
    /** Vẽ [doc] vào ảnh rộng đúng [widthDots] điểm (nền trắng, chữ đen). Ném ngoại lệ nếu không vẽ được. */
    fun render(doc: ReceiptDocument, widthDots: Int): GrayBitmap
}

/** Thang xám -> đen trắng bằng Floyd–Steinberg (như `toThermalMonochrome` của Android) để nét mảnh/dấu tiếng Việt không bị mất. */
object MonoConverter {
    fun toMono(gray: GrayBitmap): MonoBitmap {
        val w = gray.width; val h = gray.height
        val rowBytes = (w + 7) / 8
        val out = ByteArray(rowBytes * h)
        // sai số lan sang hàng hiện tại / hàng kế (cộng thêm 2 ô đệm hai bên)
        var cur = IntArray(w + 2); var next = IntArray(w + 2)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val old = (gray.pixels[y * w + x].toInt() and 0xFF) * 16 + cur[x + 1]
                val black = old < 128 * 16
                val err = old - if (black) 0 else 255 * 16
                if (black) out[y * rowBytes + x / 8] = (out[y * rowBytes + x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                cur[x + 2] += err * 7 / 16
                next[x] += err * 3 / 16
                next[x + 1] += err * 5 / 16
                next[x + 2] += err * 1 / 16
            }
            val t = cur; cur = next; next = t
            next.fill(0)
        }
        return MonoBitmap(w, h, out)
    }
}

/** Lệnh raster ESC/POS: `ESC @`, ảnh chia dải `GS v 0`, đẩy giấy, cắt. */
object EscPosRaster {
    /** Mỗi dải tối đa [bandRows] hàng điểm để không tràn bộ đệm máy in rẻ tiền. */
    fun encode(mono: MonoBitmap, feedLines: Int = 3, bandRows: Int = 256): ByteArray {
        val out = ArrayList<Byte>(mono.data.size + 64)
        fun put(vararg v: Int) = v.forEach { out.add(it.toByte()) }
        put(0x1B, 0x40)
        val rb = mono.rowBytes
        var y = 0
        while (y < mono.height) {
            val rows = minOf(bandRows, mono.height - y)
            put(0x1D, 0x76, 0x30, 0x00, rb and 0xFF, rb shr 8, rows and 0xFF, rows shr 8)
            for (i in y * rb until (y + rows) * rb) out.add(mono.data[i])
            y += rows
        }
        put(0x1B, 0x64, feedLines)
        put(0x1D, 0x56, 0x42, 0x00)
        return out.toByteArray()
    }
}
