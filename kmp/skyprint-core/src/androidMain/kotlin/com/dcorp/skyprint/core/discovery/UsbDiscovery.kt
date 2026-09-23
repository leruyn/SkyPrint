package com.dcorp.skyprint.core.discovery

import android.content.Context
import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import com.dcorp.skyprint.core.usb.UsbPrinterTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Liệt kê tức thời -- `UsbManager.getDeviceList()` không cần quyền, không cần chờ [timeoutMs] (REQ-003/REQ-004). */
class UsbDiscovery(private val context: Context) : PrinterDiscovery {
    override val kind = TransportKind.USB

    override fun discover(timeoutMs: Long): Flow<PrinterInfo> = flow {
        for (printer in UsbPrinterTransport.listCandidates(context)) emit(printer)
    }
}
