package com.dcorp.skyprint.core.discovery

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Kiểm 1 host có mở cổng TCP không (REQ-003, DESIGN-003) -- dùng cho dò
 * subnet ([SubnetScanner]) lẫn "nhập IP tay rồi kiểm kết nối" của app.
 * Không phân biệt "host không tồn tại" và "có tồn tại nhưng không lắng
 * nghe cổng đó" -- cả 2 đều trả `false`, đúng nhu cầu discovery (chỉ cần
 * biết có in được không).
 */
object LanProbe {
    suspend fun probe(host: String, port: Int = 9100, timeoutMs: Long = 3000): Boolean {
        val selectorManager = SelectorManager(Dispatchers.Default)
        return try {
            withTimeout(timeoutMs) {
                val socket = aSocket(selectorManager).tcp().connect(host, port)
                socket.close()
                true
            }
        } catch (e: TimeoutCancellationException) {
            false
        } catch (e: Exception) {
            false
        } finally {
            selectorManager.close()
        }
    }
}
