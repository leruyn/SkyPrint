package com.dcorp.skyprint.core.encode

import com.dcorp.skyprint.core.model.Align
import com.dcorp.skyprint.core.model.QrErrorCorrection

/**
 * Lệnh byte ESC/POS thô (DESIGN-001). Chỉ chứa các lệnh REQ-001 thật sự
 * dùng -- không phải một thư viện ESC/POS tổng quát. Lệnh nghiệp vụ POS
 * (mở két, còi, mã vạch, đảo màu) thuộc REQ-010, thêm ở CODE-010.
 */
internal object EscPosCommands {
    val INIT = byteArrayOf(0x1B, 0x40)

    fun align(mode: Align): ByteArray = byteArrayOf(0x1B, 0x61, mode.escPosCode)

    fun bold(on: Boolean): ByteArray = byteArrayOf(0x1B, 0x45, if (on) 1 else 0)

    /** `GS ! n` -- nibble cao = height multiplier - 1, nibble thấp = width multiplier - 1 (size 1 -> n=0x00, size 2 -> 0x11, ...). */
    fun size(size: Int): ByteArray {
        val multiplier = (size - 1).coerceIn(0, 7)
        val n = (multiplier shl 4) or multiplier
        return byteArrayOf(0x1D, 0x21, n.toByte())
    }

    val LINE_FEED = byteArrayOf(0x0A)

    fun feed(lines: Int): ByteArray = byteArrayOf(0x1B, 0x64, lines.coerceIn(0, 255).toByte())

    /** `GS V 66 0` -- partial cut, hỗ trợ rộng nhất trên máy in 58/80mm phổ thông (DESIGN-001). */
    val CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    /**
     * Chuỗi lệnh QR chuẩn Epson `GS ( k` (model 2 cố định -- đủ dùng cho mọi
     * đầu đọc QR phổ thông, không cần chọn model 1/micro). Thứ tự: chọn
     * model -> đặt cỡ module -> đặt mức sửa lỗi -> nạp dữ liệu -> in.
     */
    fun qrCode(data: String, moduleSize: Int, errorCorrection: QrErrorCorrection): ByteArray {
        val payload = data.encodeToByteArray()
        val storeLen = payload.size + 3

        fun gsParenK(cn: Int, fn: Int, vararg params: Byte): ByteArray {
            val pLen = params.size + 2
            return byteArrayOf(0x1D, 0x28, 0x6B, (pLen % 256).toByte(), (pLen / 256).toByte(), cn.toByte(), fn.toByte()) + params
        }

        val selectModel = gsParenK(0x31, 0x41, 0x32, 0x00)
        val setSize = gsParenK(0x31, 0x43, moduleSize.toByte())
        val setEcc = gsParenK(0x31, 0x45, errorCorrection.code.toByte())
        val storeData = byteArrayOf(0x1D, 0x28, 0x6B, (storeLen % 256).toByte(), (storeLen / 256).toByte(), 0x31, 0x50, 0x30) + payload
        val print = gsParenK(0x31, 0x51, 0x30)

        return selectModel + setSize + setEcc + storeData + print
    }

    private val Align.escPosCode: Byte
        get() = when (this) {
            Align.LEFT -> 0
            Align.CENTER -> 1
            Align.RIGHT -> 2
        }
}
