package com.dcorp.skyprint.core.discovery

import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TEST-UNIT-003 -- viết trước khi có [CompositeDiscovery] (đỏ). Fake
 * [PrinterDiscovery] để kiểm gộp + khử trùng, không cần phần cứng thật.
 */
class CompositeDiscoveryTest {

    private class FakeDiscovery(
        override val kind: TransportKind,
        private val items: List<PrinterInfo>,
        private val delayMsEach: Long = 0,
    ) : PrinterDiscovery {
        override fun discover(timeoutMs: Long): Flow<PrinterInfo> = flow {
            for (item in items) {
                if (delayMsEach > 0) delay(delayMsEach)
                emit(item)
            }
        }
    }

    private fun printer(id: String, kind: TransportKind) = PrinterInfo(id = id, name = id, kind = kind, address = id)

    @Test
    fun `gop ket qua tu nhieu transport`() = runTest {
        val usb = FakeDiscovery(TransportKind.USB, listOf(printer("usb:1", TransportKind.USB)))
        val lan = FakeDiscovery(TransportKind.LAN, listOf(printer("lan:1", TransportKind.LAN)))
        val composite = CompositeDiscovery(listOf(usb, lan))

        val results = composite.discover(1000).toListSafe()

        assertEquals(setOf("usb:1", "lan:1"), results.map { it.id }.toSet())
    }

    @Test
    fun `khu trung theo id neu 2 transport cung tra ve 1 id`() = runTest {
        val duplicate = printer("dup:1", TransportKind.USB)
        val a = FakeDiscovery(TransportKind.USB, listOf(duplicate))
        val b = FakeDiscovery(TransportKind.BT_CLASSIC, listOf(duplicate))
        val composite = CompositeDiscovery(listOf(a, b))

        val results = composite.discover(1000).toListSafe()

        assertEquals(1, results.size, "trùng id chỉ được phát ra đúng 1 lần")
    }

    @Test
    fun `mot transport khong tra gi van khong chan cac transport khac`() = runTest {
        val empty = FakeDiscovery(TransportKind.BLE, emptyList())
        val usb = FakeDiscovery(TransportKind.USB, listOf(printer("usb:1", TransportKind.USB)), delayMsEach = 10)
        val composite = CompositeDiscovery(listOf(empty, usb))

        val results = composite.discover(1000).toListSafe()

        assertEquals(listOf("usb:1"), results.map { it.id })
    }

    @Test
    fun `danh sach discovery rong tra ve flow rong`() = runTest {
        assertEquals(emptyList(), CompositeDiscovery(emptyList()).discover(1000).toListSafe())
    }

    private suspend fun Flow<PrinterInfo>.toListSafe(): List<PrinterInfo> {
        val out = mutableListOf<PrinterInfo>()
        collect { out += it }
        return out
    }
}
