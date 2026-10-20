package com.dcorp.skyprint.registry

import com.dcorp.skyprint.core.model.PaperWidth

/** Mục đích máy in. KITCHEN/BAR chỉ là nhãn phân loại; việc in phiếu bếp/bar do app quyết định. */
enum class PrinterRole { RECEIPT, KITCHEN, BAR, OTHER }

/**
 * Một máy in mạng LAN đã cấu hình. [id] do registry sinh và ổn định qua sửa tên/IP; [isDefault] tối đa một máy cho mỗi [type].
 * Tên thuộc tính `type` giữ nguyên để khoá JSON đã lưu trên thiết bị (từ bản cũ trong app) vẫn đọc được.
 */
data class PrinterProfile(
    val id: String,
    val name: String,
    val type: PrinterRole = PrinterRole.RECEIPT,
    val host: String,
    val port: Int = LanPrinterAddress.DEFAULT_PORT,
    val paperWidth: PaperWidth = PaperWidth.MM58,
    val isDefault: Boolean = false,
) {
    val address: String get() = "$host:$port"
}
