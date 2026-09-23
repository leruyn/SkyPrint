package com.dcorp.skyprint.core.discovery

import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import kotlinx.coroutines.flow.Flow

/**
 * Tìm máy in theo MỘT transport (REQ-003, DESIGN-003). USB/Bluetooth
 * Classic liệt kê tức thời (không cần quét chủ động -- thiết bị đã cắm/
 * ghép nối sẵn); LAN/BLE quét chủ động trong [timeoutMs].
 */
interface PrinterDiscovery {
    val kind: TransportKind
    fun discover(timeoutMs: Long): Flow<PrinterInfo>
}
