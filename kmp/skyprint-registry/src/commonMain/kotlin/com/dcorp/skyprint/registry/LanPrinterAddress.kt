package com.dcorp.skyprint.registry

/** Địa chỉ máy in mạng đã kiểm tra hợp lệ; [id] (`host:port`) luôn có port rõ ràng. */
data class LanPrinterAddress(val host: String, val port: Int) {
    val id: String get() = "$host:$port"

    companion object {
        /** Cổng RAW/JetDirect -- mặc định của hầu hết máy in nhiệt ESC/POS qua LAN. */
        const val DEFAULT_PORT = 9100

        /**
         * `192.168.1.50` hoặc `192.168.1.50:9100` (hostname cũng được) -> địa chỉ; null nếu rỗng,
         * host có khoảng trắng, hoặc port không phải số 1..65535. Chỉ IPv4/hostname (khớp transport).
         */
        fun parse(input: String): LanPrinterAddress? {
            val text = input.trim()
            if (text.isEmpty()) return null
            val idx = text.lastIndexOf(':')
            val host = if (idx < 0) text else text.substring(0, idx)
            val port = if (idx < 0) DEFAULT_PORT else text.substring(idx + 1).toIntOrNull() ?: return null
            if (host.isEmpty() || host.any { it.isWhitespace() || it == ':' }) return null
            if (port !in 1..65535) return null
            return LanPrinterAddress(host, port)
        }
    }
}
