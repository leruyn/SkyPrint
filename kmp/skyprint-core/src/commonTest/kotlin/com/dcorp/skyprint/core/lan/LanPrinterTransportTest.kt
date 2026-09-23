package com.dcorp.skyprint.core.lan

import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.utils.io.readByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * TEST-UNIT-005 -- [LanPrinterTransport] (REQ-005). Dùng SERVER TCP THẬT
 * chạy loopback trong cùng test (không phải máy in thật) -- xác định,
 * nhanh, không phụ thuộc mạng ngoài, đúng tinh thần "fake printer server"
 * đã ghi ở DESIGN-005's Dependencies.
 *
 * Không test `CONNECT_TIMEOUT` bằng địa chỉ mạng thật (dễ chập chờn theo
 * môi trường sandbox/CI: có nơi trả "unreachable" ngay, có nơi treo đúng
 * như kỳ vọng) -- nhánh `withTimeout` bọc quanh `connect()` đã tự nó đúng
 * theo cấu trúc code (coroutine timeout là cơ chế đã được kiểm chứng của
 * kotlinx.coroutines, không phải logic tự viết cần test riêng ở đây).
 */
class LanPrinterTransportTest {

    private fun printer(host: String, port: Int) =
        PrinterInfo(id = "lan:$host:$port", name = "Test", kind = TransportKind.LAN, address = "$host:$port")

    @Test
    fun `parseAddress tach dung host va port`() {
        assertEquals("192.168.1.50" to 9100, LanPrinterTransport.parseAddress("192.168.1.50", 9100))
        assertEquals("192.168.1.50" to 9101, LanPrinterTransport.parseAddress("192.168.1.50:9101", 9100))
        assertEquals("printer.local" to 9100, LanPrinterTransport.parseAddress("printer.local:abc", 9100))
    }

    @Test
    fun `ket noi va ghi thanh cong toi server that`() = runBlocking {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val server = aSocket(selectorManager).tcp().bind("127.0.0.1", 0)
        val port = (server.localAddress as InetSocketAddress).port

        val received = async {
            val connection = server.accept()
            val bytes = connection.openReadChannel().readByteArray(5)
            connection.close()
            bytes
        }

        val transport = LanPrinterTransport()
        val conn = transport.open(printer("127.0.0.1", port))
        conn.write(byteArrayOf(1, 2, 3, 4, 5))
        conn.close()

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), received.await())
        server.close()
        selectorManager.close()
    }

    @Test
    fun `khong co server lang nghe tra HOST_UNREACHABLE`() = runBlocking {
        // Bind rồi đóng ngay -- cổng chắc chắn không ai lắng nghe, hệ điều hành
        // trả ECONNREFUSED gần như tức thì (không phải treo chờ timeout).
        val probeManager = SelectorManager(Dispatchers.IO)
        val probe = aSocket(probeManager).tcp().bind("127.0.0.1", 0)
        val freePort = (probe.localAddress as InetSocketAddress).port
        probe.close()
        probeManager.close()

        val transport = LanPrinterTransport(LanConfig(connectTimeoutMs = 2000))
        val ex = assertFailsWithPrinterException { transport.open(printer("127.0.0.1", freePort)) }
        assertEquals(PrinterErrorCode.HOST_UNREACHABLE, ex.code)
    }

    @Test
    fun `mat ket noi khi dang ghi tra DISCONNECTED`() = runBlocking {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val server = aSocket(selectorManager).tcp().bind("127.0.0.1", 0)
        val port = (server.localAddress as InetSocketAddress).port

        val acceptedAndClosed = async {
            val connection = server.accept()
            connection.close() // đóng ngay -- mô phỏng máy in ngắt kết nối giữa chừng
        }

        val transport = LanPrinterTransport()
        val conn = transport.open(printer("127.0.0.1", port))
        acceptedAndClosed.await()

        // Ghi liên tục tới khi phía kia đã đóng chắc chắn lộ ra lỗi (không phải lúc
        // nào byte đầu tiên cũng phát hiện được ngay do buffer OS, nên thử vài lần).
        val ex = assertFailsWithPrinterException {
            repeat(20) { conn.write(ByteArray(4096)) }
        }
        assertEquals(PrinterErrorCode.DISCONNECTED, ex.code)

        server.close()
        selectorManager.close()
    }

    private suspend fun assertFailsWithPrinterException(block: suspend () -> Unit): PrinterException {
        try {
            block()
        } catch (e: PrinterException) {
            return e
        }
        throw AssertionError("phải ném PrinterException")
    }
}
