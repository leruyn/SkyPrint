package com.dcorp.skyprint.core.ble

import com.juul.kable.Identifier
import com.juul.kable.Peripheral

/**
 * jvm() target ở module này chỉ để chạy `commonTest` nhanh trên JVM, không
 * ship BLE thật -- backend btleplug của Kable trên JVM không hỗ trợ
 * reconnect bằng identifier thô (xem [createBlePeripheral]).
 */
internal actual fun createBlePeripheral(identifier: Identifier): Peripheral =
    throw UnsupportedOperationException(
        "BLE không được hỗ trợ trên JVM target -- chỉ dùng cho Android/iOS.",
    )
