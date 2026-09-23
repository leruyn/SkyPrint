package com.dcorp.skyprint.core.ble

import com.juul.kable.Identifier
import com.juul.kable.Peripheral

/**
 * `Peripheral(Identifier)` chỉ tồn tại ở backend Android/iOS của Kable --
 * backend JVM (btleplug) không hỗ trợ reconnect bằng identifier thô, chỉ
 * có API dựa trên [com.juul.kable.Advertisement] (cần scan trước). jvm()
 * target ở module này chỉ dùng để chạy `commonTest` nhanh trên JVM, không
 * ship BLE thật -- nên actual JVM ném lỗi thay vì cố cài đặt tương đương.
 */
internal expect fun createBlePeripheral(identifier: Identifier): Peripheral
