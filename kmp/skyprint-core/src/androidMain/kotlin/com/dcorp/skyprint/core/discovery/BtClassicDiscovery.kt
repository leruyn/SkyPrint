package com.dcorp.skyprint.core.discovery

import android.content.Context
import com.dcorp.skyprint.core.bt.BtClassicPrinterTransport
import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Liệt kê tức thời -- chỉ thiết bị ĐÃ GHÉP NỐI, không quét chủ động (REQ-003/REQ-006). */
class BtClassicDiscovery(private val context: Context) : PrinterDiscovery {
    override val kind = TransportKind.BT_CLASSIC

    override fun discover(timeoutMs: Long): Flow<PrinterInfo> = flow {
        for (printer in BtClassicPrinterTransport.listCandidates(context)) emit(printer)
    }
}
