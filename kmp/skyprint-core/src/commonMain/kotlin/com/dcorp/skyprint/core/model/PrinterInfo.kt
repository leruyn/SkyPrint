package com.dcorp.skyprint.core.model

/**
 * Model tối thiểu từ DESIGN-003 (discovery) -- đưa vào đây SỚM vì REQ-008's
 * [com.dcorp.skyprint.core.transport.PrinterTransport] cần kiểu này để khai
 * `open(printer: PrinterInfo)`. Logic tìm máy in thật (quét USB/BLE/LAN/bonded
 * Bluetooth) CHƯA cài -- đó vẫn là REQ-003, chưa tới lượt code.
 */
enum class TransportKind { USB, LAN, BT_CLASSIC, BLE }

/**
 * [id] ổn định qua nhiều lần mở app (DESIGN-003): `usb:<vendorId>:<productId>`,
 * `bt:<MAC>`, `ble:<MAC|CB-UUID>`, `lan:<host>:<port>` -- KHÔNG dùng tên
 * thiết bị hệ điều hành trả về (đổi mỗi lần cắm lại USB).
 */
data class PrinterInfo(
    val id: String,
    val name: String,
    val kind: TransportKind,
    val address: String,
    val extras: Map<String, String> = emptyMap(),
)
