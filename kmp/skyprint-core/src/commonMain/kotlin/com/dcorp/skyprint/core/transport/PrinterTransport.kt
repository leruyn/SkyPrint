package com.dcorp.skyprint.core.transport

import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind

/**
 * Kết nối đang mở tới MỘT máy in (REQ-008, DESIGN-008). Vòng đời do
 * [PrintQueue] quản -- gọi [write] đúng 1 lần rồi luôn [close], kể cả khi
 * [write] lỗi.
 */
interface PrinterConnection {
    suspend fun write(bytes: ByteArray)
    suspend fun close()
}

/**
 * Một cách nói chuyện với phần cứng (USB/LAN/BT_CLASSIC/BLE, REQ-004..007).
 * [open] chỉ nên ném [com.dcorp.skyprint.core.error.PrinterException] --
 * lỗi khác bị [PrintQueue] coi là [com.dcorp.skyprint.core.error.PrinterErrorCode.UNKNOWN].
 */
interface PrinterTransport {
    val kind: TransportKind
    suspend fun open(printer: PrinterInfo): PrinterConnection
}
