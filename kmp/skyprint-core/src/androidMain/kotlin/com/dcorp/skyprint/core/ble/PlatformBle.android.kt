package com.dcorp.skyprint.core.ble

import com.juul.kable.Identifier
import com.juul.kable.Peripheral

internal actual fun createBlePeripheral(identifier: Identifier): Peripheral = Peripheral(identifier)
