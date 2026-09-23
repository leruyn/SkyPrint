package com.dcorp.skyprint.core.encode

import com.dcorp.skyprint.core.model.Align
import com.dcorp.skyprint.core.model.BarcodeType
import com.dcorp.skyprint.core.model.BuzzerCommand
import com.dcorp.skyprint.core.model.QrErrorCorrection

/**
 * Lệnh byte ESC/POS thô (DESIGN-001, mở rộng nghiệp vụ POS ở DESIGN-010:
 * mở két, còi, đảo màu, gạch chân, mã vạch). Chỉ chứa lệnh thư viện thật sự
 * dùng -- không phải một thư viện ESC/POS tổng quát.
 */
internal object EscPosCommands {
    val INIT = byteArrayOf(0x1B, 0x40)

    fun align(mode: Align): ByteArray = byteArrayOf(0x1B, 0x61, mode.escPosCode)

    fun bold(on: Boolean): ByteArray = byteArrayOf(0x1B, 0x45, if (on) 1 else 0)

    /** `ESC - n` -- gạch chân (0 tắt, 1 gạch mảnh). */
    fun underline(on: Boolean): ByteArray = byteArrayOf(0x1B, 0x2D, if (on) 1 else 0)

    /** `GS B n` -- đảo màu (chữ trắng nền đen), dùng cho phiếu huỷ món nổi bật (REQ-010). */
    fun inverse(on: Boolean): ByteArray = byteArrayOf(0x1D, 0x42, if (on) 1 else 0)

    /** `GS ! n` -- nibble cao = height-1, nibble thấp = width-1, mỗi chiều 1..8 độc lập (REQ-010). */
    fun charSize(width: Int, height: Int): ByteArray {
        val w = (width - 1).coerceIn(0, 7)
        val h = (height - 1).coerceIn(0, 7)
        return byteArrayOf(0x1D, 0x21, ((h shl 4) or w).toByte())
    }

    val LINE_FEED = byteArrayOf(0x0A)

    fun feed(lines: Int): ByteArray = byteArrayOf(0x1B, 0x64, lines.coerceIn(0, 255).toByte())

    /** `GS V m 0` -- m=65 ('A') cắt toàn phần, m=66 ('B') cắt một phần (DESIGN-001/010). */
    fun cut(partial: Boolean): ByteArray = byteArrayOf(0x1D, 0x56, if (partial) 0x42 else 0x41, 0x00)

    /** `ESC p m t1 t2` -- mở két qua chân [pin] (0/1). t1=t2 (đối xứng, đơn giản cho v1), mỗi đơn vị ~2ms. */
    fun drawer(pin: Int, pulseMs: Int): ByteArray {
        val t = (pulseMs / 2).coerceIn(1, 255)
        return byteArrayOf(0x1B, 0x70, pin.toByte(), t.toByte(), t.toByte())
    }

    /** `ESC B n t` -- còi kêu [times] lần ([n]), mỗi lần dài [durationMs]/100 đơn vị ([t]) (chuẩn Xprinter/phổ thông). */
    fun beepEscB(times: Int, durationMs: Int): ByteArray =
        byteArrayOf(0x1B, 0x42, times.toByte(), (durationMs / 100).coerceIn(1, 9).toByte())

    /**
     * `ESC ( A` -- biến thể còi tương thích Epson (một số máy in không hiểu
     * `ESC B`). Cấu trúc giống nhóm lệnh `GS ( k`: pL pH fn rồi tham số.
     */
    fun beepEpsonA(times: Int, durationMs: Int): ByteArray {
        val params = byteArrayOf(0x30, times.toByte(), (durationMs / 100).coerceIn(1, 9).toByte())
        val pLen = params.size + 1
        return byteArrayOf(0x1B, 0x28, 0x41, (pLen % 256).toByte(), (pLen / 256).toByte()) + params
    }

    fun beep(command: BuzzerCommand, times: Int, durationMs: Int): ByteArray = when (command) {
        BuzzerCommand.ESC_B -> beepEscB(times, durationMs)
        BuzzerCommand.EPSON_A -> beepEpsonA(times, durationMs)
    }

    /** `GS H n` -- vị trí chữ số HRI dưới mã vạch (0=không in, 2=dưới mã vạch -- mặc định phổ biến nhất). */
    fun barcodeHri(show: Boolean): ByteArray = byteArrayOf(0x1D, 0x48, if (show) 2 else 0)

    /** `GS h n` -- chiều cao mã vạch tính theo dot. */
    fun barcodeHeight(dots: Int): ByteArray = byteArrayOf(0x1D, 0x68, dots.coerceIn(1, 255).toByte())

    /**
     * `GS k` -- CODE128 dùng cú pháp mới (`m n d1..dn`, cần tiền tố code-set
     * `{B` nếu data chưa tự mang tiền tố); các loại còn lại dùng cú pháp cũ
     * (`m d1..dk NUL`), theo đúng bảng mã trong [BarcodeType].
     */
    fun barcode(type: BarcodeType, data: String): ByteArray {
        val newSymbol = type.newStyleSymbol
        return if (type.oldStyleSymbol == null) {
            val payload = (if (data.startsWith("{")) data else "{B$data").encodeToByteArray()
            byteArrayOf(0x1D, 0x6B, newSymbol.toByte(), payload.size.toByte()) + payload
        } else {
            val payload = data.encodeToByteArray()
            byteArrayOf(0x1D, 0x6B, type.oldStyleSymbol.toByte()) + payload + byteArrayOf(0x00)
        }
    }

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
