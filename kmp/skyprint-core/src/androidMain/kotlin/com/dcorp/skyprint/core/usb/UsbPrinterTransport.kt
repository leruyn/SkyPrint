package com.dcorp.skyprint.core.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import com.dcorp.skyprint.core.transport.ChunkedWriter
import com.dcorp.skyprint.core.transport.PrinterConnection
import com.dcorp.skyprint.core.transport.PrinterTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [chunkSize]: một số bộ điều khiển USB đời cũ giới hạn 16KB/lần transfer, 4KB an toàn cho phần lớn máy in nhiệt. */
data class UsbConfig(
    val chunkSize: Int = 4096,
    val writeTimeoutMs: Int = 5000,
    val permissionTimeoutMs: Long = 30_000,
)

/**
 * Transport USB Printer-class (REQ-004, DESIGN-004) -- máy in cắm cáp
 * USB-B/USB-C thường (không phải qua chip serial, xem REQ-011's USB-serial
 * cho trường hợp đó). `UsbManager.getDeviceList()` không cần quyền để liệt
 * kê ([listCandidates]) -- chỉ `openDevice()` mới cần, xin ở [open].
 */
class UsbPrinterTransport(
    private val context: Context,
    private val config: UsbConfig = UsbConfig(),
) : PrinterTransport {
    override val kind = TransportKind.USB

    override suspend fun open(printer: PrinterInfo): PrinterConnection {
        val usbManager = usbManagerOf(context)
        val (vendorId, productId) = parseAddress(printer.address)

        // Không tìm theo deviceName lưu từ trước -- deviceName đổi mỗi lần cắm lại
        // (bug thật đã gặp ở bản legacy: "detect đúng qua dumpsys nhưng không bao
        // giờ connect được" vì code cũ tìm theo deviceName cũ). vendorId/productId
        // ổn định, đúng theo id ở DESIGN-003.
        val device = usbManager.deviceList.values.firstOrNull { it.vendorId == vendorId && it.productId == productId }
            ?: throw PrinterException(PrinterErrorCode.NOT_FOUND, "Không tìm thấy máy in USB '${printer.name}' -- kiểm tra lại dây cắm.")

        val usbInterface = findPrinterInterface(device)
            ?: throw PrinterException(PrinterErrorCode.UNSUPPORTED_DEVICE, "Thiết bị USB này không có USB Printer interface.")
        val endpoint = findBulkOutEndpoint(usbInterface)
            ?: throw PrinterException(PrinterErrorCode.UNSUPPORTED_DEVICE, "Không tìm thấy kênh ghi dữ liệu (bulk OUT) trên máy in USB này.")

        val granted = UsbPermissionGate.ensure(context, usbManager, device, config.permissionTimeoutMs)
        if (!granted) {
            throw PrinterException(
                PrinterErrorCode.PERMISSION_DENIED,
                "Không được cấp quyền truy cập máy in USB -- thử in lại và chọn \"Cho phép\" ở hộp thoại hệ thống.",
            )
        }

        val connection = usbManager.openDevice(device)
            ?: throw PrinterException(PrinterErrorCode.UNKNOWN, "Không mở được kết nối tới máy in USB.")
        if (!connection.claimInterface(usbInterface, true)) {
            connection.close()
            throw PrinterException(PrinterErrorCode.UNKNOWN, "Không chiếm được interface USB của máy in.")
        }

        return UsbPrinterConnection(connection, usbInterface, endpoint, config)
    }

    companion object {
        fun listCandidates(context: Context): List<PrinterInfo> {
            val usbManager = usbManagerOf(context)
            return usbManager.deviceList.values
                .filter { findPrinterInterface(it) != null }
                .map { device ->
                    val name = device.productName?.takeIf { it.isNotBlank() } ?: device.deviceName
                    PrinterInfo(
                        id = "usb:${device.vendorId}:${device.productId}",
                        name = name,
                        kind = TransportKind.USB,
                        address = "${device.vendorId}:${device.productId}",
                    )
                }
        }

        private fun usbManagerOf(context: Context): UsbManager =
            context.getSystemService(Context.USB_SERVICE) as? UsbManager
                ?: throw PrinterException(PrinterErrorCode.UNSUPPORTED_PLATFORM, "Thiết bị này không hỗ trợ USB Host.")

        private fun parseAddress(address: String): Pair<Int, Int> {
            val parts = address.split(":")
            val vendorId = parts.getOrNull(0)?.toIntOrNull()
            val productId = parts.getOrNull(1)?.toIntOrNull()
            if (vendorId == null || productId == null) {
                throw PrinterException(PrinterErrorCode.NOT_FOUND, "PrinterInfo.address USB không hợp lệ: '$address' (cần dạng 'vendorId:productId').")
            }
            return vendorId to productId
        }

        internal fun findPrinterInterface(device: UsbDevice): UsbInterface? = pickPrinterInterface(device.interfaces())

        internal fun findBulkOutEndpoint(usbInterface: UsbInterface): UsbEndpoint? = pickBulkOutEndpoint(usbInterface.endpoints())

        /**
         * Logic chọn thuần, tách khỏi việc duyệt [UsbDevice]/[UsbInterface] thật --
         * test được bằng [UsbInterface]/[UsbEndpoint] dựng trực tiếp qua constructor
         * public của chúng (Robolectric không cần shadow riêng cho 2 lớp này).
         */
        internal fun pickPrinterInterface(interfaces: List<UsbInterface>): UsbInterface? =
            interfaces.firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER }

        internal fun pickBulkOutEndpoint(endpoints: List<UsbEndpoint>): UsbEndpoint? =
            endpoints.firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }

        private fun UsbDevice.interfaces(): List<UsbInterface> = (0 until interfaceCount).map { getInterface(it) }
        private fun UsbInterface.endpoints(): List<UsbEndpoint> = (0 until endpointCount).map { getEndpoint(it) }
    }
}

private class UsbPrinterConnection(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val endpoint: UsbEndpoint,
    private val config: UsbConfig,
) : PrinterConnection {
    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        ChunkedWriter.write(bytes, config.chunkSize) { offset, length ->
            connection.bulkTransfer(endpoint, bytes, offset, length, config.writeTimeoutMs)
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            connection.releaseInterface(usbInterface)
        } catch (ignored: Exception) {
            // Interface có thể đã mất hiệu lực nếu thiết bị vừa rút -- không quan trọng nữa lúc đóng.
        }
        connection.close()
    }
}
