package com.dcorp.skyprint.core.discovery

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TEST-UNIT-003b -- [LanProbe] (viết trước, đỏ) dùng server TCP THẬT chạy
 * loopback (như REQ-005's LanPrinterTransportTest) -- không phải máy in
 * thật nhưng là mạng/OS thật, không mock. [SubnetScanner] test bằng
 * `probe` giả (thuần, không cần mạng) vì bản thân thuật toán quét đồng
 * thời/lọc không phụ thuộc I/O thật.
 */
class LanProbeTest {
    @Test
    fun `probe tra true khi co server lang nghe`() = runBlocking {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val server = aSocket(selectorManager).tcp().bind("127.0.0.1", 0)
        val port = (server.localAddress as InetSocketAddress).port

        assertTrue(LanProbe.probe("127.0.0.1", port, timeoutMs = 2000))

        server.close()
        selectorManager.close()
    }

    @Test
    fun `probe tra false khi khong co ai lang nghe`() = runBlocking {
        val probeManager = SelectorManager(Dispatchers.Default)
        val closedServer = aSocket(probeManager).tcp().bind("127.0.0.1", 0)
        val freePort = (closedServer.localAddress as InetSocketAddress).port
        closedServer.close()
        probeManager.close()

        assertFalse(LanProbe.probe("127.0.0.1", freePort, timeoutMs = 1000))
    }
}

class SubnetScannerTest {
    @Test
    fun `chi tra ve host duoc probe xac nhan true`() = runBlocking {
        val alive = setOf("192.168.1.10", "192.168.1.20")
        val result = SubnetScanner.scan(
            hosts = listOf("192.168.1.1", "192.168.1.10", "192.168.1.20", "192.168.1.254"),
            port = 9100,
            timeoutMs = 100,
        ) { host, _, _ -> host in alive }

        assertEquals(alive, result.toSet())
    }

    @Test
    fun `moi host trong danh sach deu duoc probe dung 1 lan`() = runBlocking {
        val calls = mutableListOf<String>()
        val callsMutex = Mutex()
        val hosts = (1..50).map { "192.168.1.$it" }

        SubnetScanner.scan(hosts, port = 9100, timeoutMs = 50, concurrency = 8) { host, _, _ ->
            callsMutex.withLock { calls += host }
            false
        }

        assertEquals(hosts.toSet(), calls.toSet())
        assertEquals(hosts.size, calls.size, "mỗi host chỉ được probe đúng 1 lần, không lặp/thiếu")
    }

    @Test
    fun `danh sach host rong tra ve rong ngay`() = runBlocking {
        assertEquals(emptyList(), SubnetScanner.scan(emptyList(), 9100, 100) { _, _, _ -> true })
    }
}

class LanDiscoveryTest {
    @Test
    fun `phat ra PrinterInfo cho tung host phan hoi`() = runBlocking {
        val discovery = LanDiscovery(
            subnetProvider = { listOf("192.168.1.10", "192.168.1.11", "192.168.1.12") },
            probe = { host, _, _ -> host == "192.168.1.11" },
        )

        val results = mutableListOf<com.dcorp.skyprint.core.model.PrinterInfo>()
        discovery.discover(1000).collect { results += it }

        assertEquals(1, results.size)
        assertEquals("192.168.1.11:9100", results.single().address)
        assertEquals(com.dcorp.skyprint.core.model.TransportKind.LAN, results.single().kind)
    }

    @Test
    fun `khong co subnet phat ra flow rong khong loi`() = runBlocking {
        val discovery = LanDiscovery(subnetProvider = { null }, probe = { _, _, _ -> true })
        val results = mutableListOf<com.dcorp.skyprint.core.model.PrinterInfo>()
        discovery.discover(1000).collect { results += it }
        assertEquals(emptyList(), results)
    }
}
