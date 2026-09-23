package com.dcorp.skyprint.core.discovery

import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Lấy danh sách host trong mạng LAN hiện tại để [LanDiscovery] quét --
 * tách khỏi cách lấy IP/subnet thật (khác nhau theo nền tảng, xem
 * `createPlatformLocalSubnetProvider` androidMain) để phần thuật toán
 * quét ([SubnetScanner]) không phụ thuộc API mạng riêng của Android/iOS.
 */
fun interface LocalSubnetProvider {
    /** `null` nếu không xác định được subnet hiện tại (không có Wi-Fi/Ethernet). */
    fun currentSubnetHosts(): List<String>?
}

/**
 * Dò máy in LAN bằng cách quét /24 của mạng hiện tại (REQ-003, DESIGN-003)
 * -- không tìm thấy subnet (không có mạng) thì phát ra flow rỗng, không lỗi.
 */
class LanDiscovery(
    private val subnetProvider: LocalSubnetProvider,
    private val port: Int = 9100,
    private val hostProbeTimeoutMs: Long = 300,
    private val concurrency: Int = 32,
    /** Chỉ dùng để test -- thay [LanProbe.probe] bằng hàm giả, không cần mạng thật. */
    private val probe: suspend (host: String, port: Int, timeoutMs: Long) -> Boolean = LanProbe::probe,
) : PrinterDiscovery {
    override val kind = TransportKind.LAN

    override fun discover(timeoutMs: Long): Flow<PrinterInfo> = flow {
        val hosts = subnetProvider.currentSubnetHosts().orEmpty()
        val alive = SubnetScanner.scan(hosts, port, hostProbeTimeoutMs, concurrency, probe)
        for (host in alive) {
            emit(PrinterInfo(id = "lan:$host:$port", name = host, kind = TransportKind.LAN, address = "$host:$port"))
        }
    }
}
