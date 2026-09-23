package com.dcorp.skyprint.core.ble

import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import com.dcorp.skyprint.core.transport.PrinterConnection
import com.dcorp.skyprint.core.transport.PrinterTransport
import com.juul.kable.DiscoveredCharacteristic
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import com.juul.kable.toIdentifier
import com.juul.kable.write
import com.juul.kable.writeWithoutResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.uuid.ExperimentalUuidApi

/**
 * Transport BLE (REQ-007, DESIGN-007) qua Kable -- `commonMain`, chạy được
 * Android và iOS không cần code riêng nền tảng (giống REQ-005's LAN, khác
 * REQ-004's USB).
 *
 * KHÔNG có test với thiết bị BLE thật (khác REQ-004 -- lúc đó có sẵn ACE3 +
 * ICOD_Thermal_Printer để verify; máy in BLE hiện chưa có sẵn để test).
 * Kable cũng không cung cấp fake/mock cho BLE stack. Phần logic ĐÃ test
 * được (không phụ thuộc thiết bị) đã tách riêng vào [CharacteristicResolver]
 * -- phần còn lại (connect/discover services/write thật) cần xác minh trên
 * thiết bị thật trước khi tin tưởng hoàn toàn, giống cách REQ-002's
 * AndroidTextRasterizer vẫn còn treo việc verify.
 */
class BlePrinterTransport(private val config: BleConfig = BleConfig()) : PrinterTransport {
    override val kind = TransportKind.BLE

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun open(printer: PrinterInfo): PrinterConnection {
        val peripheral = connect(printer)

        val services = peripheral.services.value.orEmpty()
        val candidates = services.flatMap { service ->
            service.characteristics.map { characteristic ->
                BleCharacteristicInfo(
                    serviceUuid = service.serviceUuid.toString(),
                    characteristicUuid = characteristic.characteristicUuid.toString(),
                    writableWithResponse = characteristic.properties.write,
                    writableWithoutResponse = characteristic.properties.writeWithoutResponse,
                )
            }
        }

        val resolved = CharacteristicResolver.resolve(candidates, config)
        if (resolved == null) {
            try {
                peripheral.disconnect()
            } catch (ignored: Exception) {
                // Không quan trọng nữa -- sắp báo lỗi UNSUPPORTED_DEVICE cho caller.
            }
            throw PrinterException(
                PrinterErrorCode.UNSUPPORTED_DEVICE,
                "Không tìm thấy characteristic ghi được trên máy in BLE '${printer.name}'.",
            )
        }

        val characteristic = services
            .first { it.serviceUuid.toString().equals(resolved.serviceUuid, ignoreCase = true) }
            .characteristics
            .first { it.characteristicUuid.toString().equals(resolved.characteristicUuid, ignoreCase = true) }

        val writeType = if (resolved.writableWithResponse) WriteType.WithResponse else WriteType.WithoutResponse
        return BlePrinterConnection(peripheral, characteristic, writeType, config)
    }

    private suspend fun connect(printer: PrinterInfo): Peripheral {
        val peripheral = Peripheral(printer.address.toIdentifier())
        try {
            withTimeout(config.connectTimeoutMs) { peripheral.connect() }
        } catch (e: TimeoutCancellationException) {
            throw PrinterException(
                PrinterErrorCode.CONNECT_TIMEOUT,
                "Không kết nối được máy in BLE '${printer.name}' trong ${config.connectTimeoutMs}ms.",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw PrinterException(
                PrinterErrorCode.DISCONNECTED,
                "Không kết nối được máy in BLE '${printer.name}' -- ${e.message ?: e::class.simpleName}",
                e,
            )
        }
        return peripheral
    }
}

private class BlePrinterConnection(
    private val peripheral: Peripheral,
    private val characteristic: DiscoveredCharacteristic,
    private val writeType: WriteType,
    private val config: BleConfig,
) : PrinterConnection {
    override suspend fun write(bytes: ByteArray) {
        try {
            // MTU trừ 3 byte header ATT -- xem DESIGN-007's risk; mặc định an toàn 20 nếu
            // truy vấn MTU thất bại (giá trị BLE tối thiểu theo chuẩn, luôn hoạt động được).
            val mtu = try {
                peripheral.maximumWriteValueLengthForType(writeType)
            } catch (e: Exception) {
                20
            }
            val chunkSize = (mtu - 3).coerceAtLeast(20)

            var offset = 0
            while (offset < bytes.size) {
                val length = minOf(chunkSize, bytes.size - offset)
                peripheral.write(characteristic, bytes.copyOfRange(offset, offset + length), writeType)
                offset += length
                if (writeType == WriteType.WithoutResponse && config.withoutResponsePacingMs > 0) {
                    delay(config.withoutResponsePacingMs)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw PrinterException(
                PrinterErrorCode.DISCONNECTED,
                "Mất kết nối tới máy in BLE khi đang ghi -- ${e.message ?: e::class.simpleName}",
                e,
            )
        }
    }

    override suspend fun close() {
        try {
            peripheral.disconnect()
        } catch (ignored: Exception) {
            // Kết nối có thể đã mất sẵn (máy in tắt/ra khỏi tầm) -- không quan trọng nữa lúc đóng.
        }
    }
}
