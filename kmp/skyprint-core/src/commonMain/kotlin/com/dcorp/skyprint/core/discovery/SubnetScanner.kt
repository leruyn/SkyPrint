package com.dcorp.skyprint.core.discovery

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Quét đồng thời [hosts] bằng [probe] (mặc định [LanProbe.probe]), tối đa
 * [concurrency] kết nối song song (REQ-003, DESIGN-003: "32 kết nối song
 * song") -- tách [probe] thành tham số để test bằng hàm giả, không cần
 * mạng thật.
 */
object SubnetScanner {
    suspend fun scan(
        hosts: List<String>,
        port: Int,
        timeoutMs: Long,
        concurrency: Int = 32,
        // null thay vì gán trực tiếp `LanProbe::probe` / lambda gọi suspend --
        // cả 2 dạng default value đều trigger crash JVM IR backend của Kotlin
        // 2.4.10 (AddContinuationLowering: "has no continuation"). Gán lambda
        // thật trong thân hàm để né khỏi signature.
        probe: (suspend (host: String, port: Int, timeoutMs: Long) -> Boolean)? = null,
    ): List<String> = coroutineScope {
        if (hosts.isEmpty()) return@coroutineScope emptyList()
        val doProbe = probe ?: { host, port, timeoutMs -> LanProbe.probe(host, port, timeoutMs) }
        val semaphore = Semaphore(concurrency.coerceAtLeast(1))
        hosts
            .map { host -> async { semaphore.withPermit { if (doProbe(host, port, timeoutMs)) host else null } } }
            .awaitAll()
            .filterNotNull()
    }
}
