package com.dcorp.skyprint.core.lan

import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import com.dcorp.skyprint.core.transport.PrinterConnection
import com.dcorp.skyprint.core.transport.PrinterTransport
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.writeByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * [defaultPort]: 9100 (JetDirect/RAW), cổng chuẩn de-facto cho máy in ESC/POS
 * qua LAN -- dùng khi [PrinterInfo.address] chỉ có host, không có `:port`.
 */
data class LanConfig(
    val defaultPort: Int = 9100,
    val connectTimeoutMs: Long = 3000,
    val writeTimeoutMs: Long = 10_000,
)

/**
 * Transport LAN qua raw TCP (REQ-005, DESIGN-005) -- chạy được cả Android
 * và iOS vì dùng `ktor-network` (commonMain), không phải API riêng nền
 * tảng như REQ-004's USB.
 */
class LanPrinterTransport(private val config: LanConfig = LanConfig()) : PrinterTransport {
    override val kind = TransportKind.LAN

    override suspend fun open(printer: PrinterInfo): PrinterConnection {
        val (host, port) = parseAddress(printer.address, config.defaultPort)
        val selectorManager = SelectorManager(Dispatchers.Default)

        val socket = try {
            withTimeout(config.connectTimeoutMs) {
                aSocket(selectorManager).tcp().connect(host, port)
            }
        } catch (e: TimeoutCancellationException) {
            selectorManager.close()
            throw PrinterException(
                PrinterErrorCode.CONNECT_TIMEOUT,
                "Không kết nối được máy in LAN $host:$port trong ${config.connectTimeoutMs}ms.",
            )
        } catch (e: Exception) {
            selectorManager.close()
            throw PrinterException(
                PrinterErrorCode.HOST_UNREACHABLE,
                "Không tới được máy in LAN $host:$port -- ${e.message ?: e::class.simpleName}",
                e,
            )
        }

        return LanPrinterConnection(socket, selectorManager, config)
    }

    internal companion object {
        /** `host:port` -> cặp; thiếu `:port` (hoặc port không phải số) thì dùng [defaultPort]. Không tách nhầm IPv6 vì v1 chỉ nhắm IPv4 (máy in LAN thực tế hầu như luôn IPv4). */
        internal fun parseAddress(address: String, defaultPort: Int): Pair<String, Int> {
            val idx = address.lastIndexOf(':')
            if (idx <= 0) return address to defaultPort
            val host = address.substring(0, idx)
            val port = address.substring(idx + 1).toIntOrNull() ?: return host to defaultPort
            return host to port
        }
    }
}

private class LanPrinterConnection(
    private val socket: Socket,
    private val selectorManager: SelectorManager,
    private val config: LanConfig,
) : PrinterConnection {
    override suspend fun write(bytes: ByteArray) {
        try {
            withTimeout(config.writeTimeoutMs) {
                val channel = socket.openWriteChannel(autoFlush = false)
                channel.writeByteArray(bytes)
                channel.flush()
            }
        } catch (e: TimeoutCancellationException) {
            throw PrinterException(PrinterErrorCode.DISCONNECTED, "Ghi dữ liệu tới máy in LAN quá thời gian cho phép (${config.writeTimeoutMs}ms).")
        } catch (e: Exception) {
            throw PrinterException(PrinterErrorCode.DISCONNECTED, "Mất kết nối tới máy in LAN khi đang ghi -- ${e.message ?: e::class.simpleName}", e)
        }
    }

    override suspend fun close() {
        try {
            socket.close()
        } catch (ignored: Exception) {
            // Socket có thể đã hỏng sẵn (peer đóng trước) -- không quan trọng nữa lúc đóng.
        }
        selectorManager.close()
    }
}
