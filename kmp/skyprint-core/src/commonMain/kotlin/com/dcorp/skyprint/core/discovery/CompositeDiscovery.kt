package com.dcorp.skyprint.core.discovery

import com.dcorp.skyprint.core.model.PrinterInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gộp kết quả [discoveries] chạy SONG SONG thành một luồng, khử trùng theo
 * [PrinterInfo.id] (REQ-003, DESIGN-003) -- máy in xuất hiện qua nhiều
 * transport (hiếm nhưng có thể, vd USB + BLE cùng lúc) chỉ phát ra đúng 1
 * lần, theo đúng lần đầu tìm thấy. `channelFlow` tự đóng kênh khi mọi
 * transport con đã phát xong (không cần đóng tay) và tự huỷ các job con
 * nếu người thu thập huỷ giữa chừng.
 */
class CompositeDiscovery(private val discoveries: List<PrinterDiscovery>) {
    fun discover(timeoutMs: Long): Flow<PrinterInfo> = channelFlow {
        val seenIds = mutableSetOf<String>()
        val seenLock = Mutex()

        val jobs = discoveries.map { discovery ->
            launch {
                discovery.discover(timeoutMs).collect { info ->
                    val isNew = seenLock.withLock { seenIds.add(info.id) }
                    if (isNew) send(info)
                }
            }
        }
        jobs.joinAll()
    }
}
