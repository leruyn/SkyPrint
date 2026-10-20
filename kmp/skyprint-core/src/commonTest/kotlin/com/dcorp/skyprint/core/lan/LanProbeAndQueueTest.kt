package com.dcorp.skyprint.core.lan

import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import com.dcorp.skyprint.core.transport.PrintQueue
import com.dcorp.skyprint.core.transport.PrintResult
import com.dcorp.skyprint.core.transport.RetryPolicy
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.utils.io.readByteArray
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P3 (docs/SkyPrint-SDK-ConsolidationPlan.md của SkytabOffline): các hành vi mà app đã có ở `KtorLanTransport` và SkyPrint cần có
 * trước khi app chuyển sang dùng nó -- probe không ra giấy, mọi thao tác mạng chạy trên dispatcher riêng, nhiều lệnh in song song
 * vào MỘT máy qua [PrintQueue] không trộn byte. Server TCP thật chạy loopback như [LanPrinterTransportTest].
 */
class LanProbeAndQueueTest {
    private fun printer(port: Int) =
        PrinterInfo(id = "lan:127.0.0.1:$port", name = "Test", kind = TransportKind.LAN, address = "127.0.0.1:$port")

    @Test
    fun `probe thanh cong khi may in nhan ket noi va khong gui byte nao`() = runBlocking {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val server = aSocket(selectorManager).tcp().bind("127.0.0.1", 0)
        val port = (server.localAddress as InetSocketAddress).port
        val accepted = async { server.accept().close() }

        val result = LanPrinterTransport().probe(printer(port))

        accepted.await()
        assertEquals(PrintResult.Success, result)
        server.close()
        selectorManager.close()
    }

    @Test
    fun `probe bao HOST_UNREACHABLE khi may in tat`() = runBlocking {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val probe = aSocket(selectorManager).tcp().bind("127.0.0.1", 0)
        val freePort = (probe.localAddress as InetSocketAddress).port
        probe.close()
        selectorManager.close()

        val result = LanPrinterTransport(LanConfig(connectTimeoutMs = 2000)).probe(printer(freePort))

        val failure = result as PrintResult.Failure
        assertEquals(PrinterErrorCode.HOST_UNREACHABLE, failure.code)
        assertTrue(failure.message.contains("127.0.0.1:$freePort"), failure.message)
    }

    /** Mô phỏng "luồng chính": mọi mở/ghi/đóng phải được chuyển sang dispatcher của transport chứ không chạy ngay trên luồng người gọi. */
    @Test
    fun `mo ghi dong chay tren dispatcher rieng cua transport`() = runBlocking {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val server = aSocket(selectorManager).tcp().bind("127.0.0.1", 0)
        val port = (server.localAddress as InetSocketAddress).port
        val received = async {
            val c = server.accept()
            val bytes = c.openReadChannel().readByteArray(3)
            c.close()
            bytes
        }
        var dispatched = 0
        val counting = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                dispatched++
                Dispatchers.Default.dispatch(context, block)
            }
        }

        val conn = LanPrinterTransport(LanConfig(dispatcher = counting)).open(printer(port))
        conn.write(byteArrayOf(1, 2, 3))
        conn.close()

        assertEquals(listOf<Byte>(1, 2, 3), received.await().toList())
        assertTrue(dispatched >= 3, "open/write/close đều phải qua dispatcher của transport, dispatched=$dispatched")
        server.close()
        selectorManager.close()
    }

    @Test
    fun `nhieu lenh in dong thoi vao mot may qua PrintQueue khong tron byte`() = runBlocking {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val server = aSocket(selectorManager).tcp().bind("127.0.0.1", 0)
        val port = (server.localAddress as InetSocketAddress).port
        val size = 3000
        val jobs = 6
        val received = async {
            (1..jobs).map {
                val c = server.accept()
                val bytes = c.openReadChannel().readByteArray(size)
                c.close()
                bytes
            }
        }

        // maxRetries = 0: in hoá đơn không được tự in lại khi lỗi giữa chừng (dễ ra hai bill) -- khớp hành vi hiện tại của app.
        val queue = PrintQueue(listOf(LanPrinterTransport()), RetryPolicy(maxRetries = 0))
        val results = (1..jobs).map { i -> async { queue.print(printer(port), ByteArray(size) { i.toByte() }) } }.awaitAll()

        assertTrue(results.all { it == PrintResult.Success }, results.toString())
        val chunks = received.await()
        assertTrue(chunks.all { c -> c.all { it == c[0] } }, "mỗi bill phải nguyên vẹn, không lẫn byte của bill khác")
        assertEquals((1..jobs).map { it.toByte() }.toSet(), chunks.map { it[0] }.toSet())
        server.close()
        selectorManager.close()
    }
}
