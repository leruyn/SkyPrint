package com.dcorp.skyprint.core.transport

import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException

/**
 * Ghi [ByteArray] theo từng gói nhỏ qua một hàm truyền tải bất kỳ trả về
 * SỐ BYTE THẬT SỰ đã gửi (kiểu `bulkTransfer`/`port.write` của USB/serial) --
 * dùng chung cho REQ-004 (USB) và sau này USB-serial, không viết lại logic
 * chia gói/dò lỗi ở mỗi transport.
 *
 * Tách riêng khỏi bất kỳ API Android/hệ điều hành nào để test được bằng
 * JVM thuần, không cần thiết bị/Robolectric -- đây là chỗ dễ có bug nhất
 * (bản gốc DantSu/legacy đã review đầu phiên: `transferred >= 0` coi là
 * thành công, nên `transferred == 0` khiến vòng lặp gửi KHÔNG BAO GIỜ kết
 * thúc vì `offset` không tăng).
 */
internal object ChunkedWriter {
    suspend fun write(bytes: ByteArray, chunkSize: Int, transfer: suspend (offset: Int, length: Int) -> Int) {
        require(chunkSize > 0) { "chunkSize phải > 0, nhận $chunkSize" }

        var offset = 0
        while (offset < bytes.size) {
            val length = minOf(chunkSize, bytes.size - offset)
            val transferred = transfer(offset, length)
            if (transferred <= 0) {
                throw PrinterException(
                    code = PrinterErrorCode.WRITE_FAILED,
                    message = "Ghi dữ liệu thất bại tại offset=$offset/${bytes.size} (transfer trả về $transferred).",
                )
            }
            offset += transferred
        }
    }
}
