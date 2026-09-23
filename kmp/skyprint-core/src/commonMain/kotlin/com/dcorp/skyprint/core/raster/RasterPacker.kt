package com.dcorp.skyprint.core.raster

import com.dcorp.skyprint.core.model.MonoBitmap

/**
 * Đóng gói [MonoBitmap] thành lệnh ESC/POS `GS v 0` (REQ-002, DESIGN-002).
 * Chia theo dải [bandHeight] dòng dot -- máy in nhiệt rẻ tiền có buffer nhỏ,
 * gửi nguyên ảnh cao hàng nghìn dot trong 1 lệnh dễ tràn buffer/treo máy;
 * nhiều lệnh `GS v 0` liên tiếp (mỗi lệnh 1 dải) an toàn hơn dù cùng tổng
 * dữ liệu.
 */
object RasterPacker {
    fun toGsV0(bitmap: MonoBitmap, bandHeight: Int = 255): ByteArray {
        require(bandHeight in 1..255) { "bandHeight phải trong khoảng 1..255, nhận $bandHeight" }
        if (bitmap.height == 0) return ByteArray(0)

        val widthBytes = (bitmap.width + 7) / 8
        val out = mutableListOf<Byte>()
        var y = 0
        while (y < bitmap.height) {
            val rows = minOf(bandHeight, bitmap.height - y)
            // GS v 0 m xL xH yL yH d1..dk -- m=0 (kích thước thường, không nhân đôi).
            out.addAll(
                byteArrayOf(
                    0x1D, 0x76, 0x30, 0x00,
                    (widthBytes % 256).toByte(), (widthBytes / 256).toByte(),
                    (rows % 256).toByte(), (rows / 256).toByte(),
                ).toList(),
            )
            val start = y * widthBytes
            val end = start + rows * widthBytes
            out.addAll(bitmap.bits.copyOfRange(start, end).toList())
            y += rows
        }
        return out.toByteArray()
    }
}
