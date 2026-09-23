package com.dcorp.skyprint.core.bt

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import com.dcorp.skyprint.core.transport.PrinterConnection
import com.dcorp.skyprint.core.transport.PrinterTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** [interChunkDelayMs]: máy in Bluetooth Classic giá rẻ buffer nhỏ -- nghỉ giữa các gói tránh mất byte, mặc định 0 (không nghỉ) vì phần lớn máy chịu được ghi liên tục. */
data class BtConfig(
    val chunkSize: Int = 1024,
    val interChunkDelayMs: Long = 0,
    val insecureFallback: Boolean = true,
    val connectTimeoutMs: Long = 10_000,
)

/**
 * Transport Bluetooth Classic qua SPP (REQ-006, DESIGN-006) -- chỉ nói
 * chuyện với thiết bị ĐÃ GHÉP NỐI qua Cài đặt hệ thống trước (không có
 * discovery/pairing UI trong thư viện, xem PrinterDeviceDirectory's doc ở
 * DESIGN-003). Android-only -- iOS cần MFi/External Accessory, không hỗ
 * trợ SPP thường (đã ghi trong req.md's Out of scope).
 */
class BtClassicPrinterTransport(
    private val context: Context,
    private val config: BtConfig = BtConfig(),
) : PrinterTransport {
    override val kind = TransportKind.BT_CLASSIC

    override suspend fun open(printer: PrinterInfo): PrinterConnection {
        if (!hasBluetoothConnectPermission(context)) {
            throw PrinterException(
                PrinterErrorCode.PERMISSION_REQUIRED,
                "Thiếu quyền Bluetooth -- cấp quyền BLUETOOTH_CONNECT cho ứng dụng rồi thử lại.",
            )
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: throw PrinterException(PrinterErrorCode.UNSUPPORTED_PLATFORM, "Thiết bị này không hỗ trợ Bluetooth.")
        if (!adapter.isEnabled) {
            throw PrinterException(PrinterErrorCode.ADAPTER_OFF, "Bluetooth chưa bật trên thiết bị này.")
        }

        val device = findBondedDevice(adapter, printer)
            ?: throw PrinterException(
                PrinterErrorCode.NOT_FOUND,
                "Không tìm thấy máy in Bluetooth '${printer.name}' -- kiểm tra đã ghép nối trong Cài đặt chưa.",
            )

        val socket = withContext(Dispatchers.IO) {
            try {
                withTimeout(config.connectTimeoutMs) { connectSocket(adapter, device) }
            } catch (e: TimeoutCancellationException) {
                throw PrinterException(
                    PrinterErrorCode.CONNECT_TIMEOUT,
                    "Không kết nối được máy in Bluetooth '${printer.name}' trong ${config.connectTimeoutMs}ms.",
                )
            }
        }
        return BtPrinterConnection(socket, config)
    }

    private fun findBondedDevice(adapter: BluetoothAdapter, printer: PrinterInfo): BluetoothDevice? = try {
        adapter.bondedDevices?.firstOrNull { it.address == printer.address }
    } catch (e: SecurityException) {
        throw PrinterException(PrinterErrorCode.PERMISSION_REQUIRED, "Thiếu quyền Bluetooth.", e)
    }

    /** `cancelDiscovery()` không hại gì kể cả khi không có gì đang chạy -- discovery bỏ dở làm chậm/hỏng `connect()` đang gọi. */
    private fun connectSocket(adapter: BluetoothAdapter, device: BluetoothDevice): BluetoothSocket {
        adapter.cancelDiscovery()

        val secure = runCatching { device.createRfcommSocketToServiceRecord(SPP_UUID) }.getOrNull()
        if (secure != null) {
            val connected = runCatching { secure.connect() }
            if (connected.isSuccess) return secure
            runCatching { secure.close() }
            if (!config.insecureFallback) {
                throw PrinterException(
                    PrinterErrorCode.DISCONNECTED,
                    "Không kết nối được (secure) tới máy in Bluetooth -- ${connected.exceptionOrNull()?.message}",
                    connected.exceptionOrNull(),
                )
            }
        }

        val insecure = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
        val connected = runCatching { insecure.connect() }
        if (connected.isFailure) {
            runCatching { insecure.close() }
            val cause = connected.exceptionOrNull()
            throw PrinterException(
                PrinterErrorCode.DISCONNECTED,
                "Không kết nối được tới máy in Bluetooth -- ${cause?.message ?: cause?.let { it::class.simpleName }}",
                cause,
            )
        }
        return insecure
    }

    companion object {
        fun listCandidates(context: Context): List<PrinterInfo> {
            if (!hasBluetoothConnectPermission(context)) return emptyList()
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()
            return try {
                adapter.bondedDevices
                    ?.map { device ->
                        val name = device.name?.takeIf { it.isNotBlank() } ?: device.address
                        PrinterInfo(id = "bt:${device.address}", name = name, kind = TransportKind.BT_CLASSIC, address = device.address)
                    }
                    ?.sortedBy { it.name }
                    ?: emptyList()
            } catch (e: SecurityException) {
                emptyList()
            }
        }

        /** BLUETOOTH_CONNECT chỉ là quyền runtime (dangerous) từ API 31 -- dưới đó BLUETOOTH/BLUETOOTH_ADMIN cấp tự động qua khai manifest, không cần kiểm lúc chạy. */
        private fun hasBluetoothConnectPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }

        /** UUID Serial Port Profile chuẩn -- mọi máy in ESC/POS Bluetooth phổ thông đăng ký kênh RFCOMM dưới UUID này. */
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

private class BtPrinterConnection(
    private val socket: BluetoothSocket,
    private val config: BtConfig,
) : PrinterConnection {
    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            var offset = 0
            while (offset < bytes.size) {
                val length = minOf(config.chunkSize, bytes.size - offset)
                socket.outputStream.write(bytes, offset, length)
                offset += length
                if (config.interChunkDelayMs > 0 && offset < bytes.size) delay(config.interChunkDelayMs)
            }
            socket.outputStream.flush()
        } catch (e: Exception) {
            throw PrinterException(
                PrinterErrorCode.DISCONNECTED,
                "Mất kết nối tới máy in Bluetooth khi đang ghi -- ${e.message ?: e::class.simpleName}",
                e,
            )
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            socket.close()
        } catch (ignored: Exception) {
            // Socket có thể đã hỏng sẵn (peer đóng trước) -- không quan trọng nữa lúc đóng.
        }
    }
}
