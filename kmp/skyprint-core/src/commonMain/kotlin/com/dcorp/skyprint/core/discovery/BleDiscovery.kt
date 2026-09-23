package com.dcorp.skyprint.core.discovery

import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import com.juul.kable.Scanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Quét BLE quảng bá (advertisement) trong [timeoutMs] (REQ-003, DESIGN-003)
 * -- qua Kable, giống [com.dcorp.skyprint.core.ble.BlePrinterTransport].
 * CHƯA test với thiết bị thật (không có máy in BLE sẵn, Kable không có
 * fake cho scan -- cùng giới hạn đã ghi ở REQ-007's BlePrinterTransport).
 */
class BleDiscovery : PrinterDiscovery {
    override val kind = TransportKind.BLE

    private val scanner = Scanner {}

    override fun discover(timeoutMs: Long): Flow<PrinterInfo> = flow {
        val seenIdentifiers = mutableSetOf<String>()
        withTimeoutOrNull(timeoutMs) {
            scanner.advertisements.collect { advertisement ->
                val identifier = advertisement.identifier.toString()
                if (seenIdentifiers.add(identifier)) {
                    val name = advertisement.peripheralName?.takeIf { it.isNotBlank() }
                        ?: advertisement.name?.takeIf { it.isNotBlank() }
                        ?: "BLE $identifier"
                    emit(PrinterInfo(id = "ble:$identifier", name = name, kind = TransportKind.BLE, address = identifier))
                }
            }
        }
    }
}
