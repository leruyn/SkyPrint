package com.dcorp.skyprint.registry

import com.dcorp.skyprint.core.model.PaperWidth
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Danh sách máy in mạng ở Master (REQ-052): CRUD có kiểm tra, "tối đa 1 mặc định mỗi loại", và [resolve] chọn máy khi in.
 * Không an toàn đa luồng -- gọi từ luồng UI / handler của Master; dữ liệu nhỏ nên mỗi thao tác nạp-ghi cả danh sách.
 */
class PrinterRegistry @OptIn(ExperimentalUuidApi::class) constructor(
    private val store: PrinterProfileStore,
    private val newId: () -> String = { Uuid.random().toString() },
) {
    fun list(): List<PrinterProfile> = store.load()

    fun add(name: String, type: PrinterRole, host: String, port: Int, paperWidth: PaperWidth): RegistryResult<PrinterProfile> {
        val all = list()
        val candidate = PrinterProfile(newId(), name.trim(), type, host.trim(), port, paperWidth, isDefault = all.none { it.type == type && it.isDefault })
        validate(candidate, all)?.let { return invalid(it) }
        store.save(all + candidate)
        return RegistryResult.Success(candidate)
    }

    /** Lưu từ form: [PrinterDraft.id] null = thêm mới, có = sửa. Cổng nhập chữ/ngoài 1–65535 bị từ chối cùng một thông báo địa chỉ. */
    fun save(draft: PrinterDraft): RegistryResult<PrinterProfile> {
        val port = draft.portText.trim().toIntOrNull() ?: return invalid(ADDRESS_ERROR)
        return if (draft.id == null) {
            add(draft.name, draft.type, draft.host, port, draft.paperWidth)
        } else {
            update(PrinterProfile(draft.id, draft.name, draft.type, draft.host, port, draft.paperWidth))
        }
    }

    /** Sửa theo [PrinterProfile.id]; cờ mặc định do [setDefault] quản lý nên giá trị `isDefault` truyền vào bị bỏ qua. */
    fun update(config: PrinterProfile): RegistryResult<PrinterProfile> {
        val all = list()
        val old = all.firstOrNull { it.id == config.id }
            ?: return RegistryResult.Failure(RegistryError(RegistryErrorCode.NOT_FOUND, "Máy in không còn trên Master — chọn lại."))
        val others = all.filter { it.id != config.id }
        val isDefault = if (config.type == old.type) old.isDefault else others.none { it.type == config.type && it.isDefault }
        val candidate = config.copy(name = config.name.trim(), host = config.host.trim(), isDefault = isDefault)
        validate(candidate, others)?.let { return invalid(it) }
        store.save(all.map { if (it.id == candidate.id) candidate else it })
        return RegistryResult.Success(candidate)
    }

    /** Xoá máy mặc định thì KHÔNG tự chọn máy khác thay -- [resolve] sẽ lấy máy đầu tiên của loại. */
    fun delete(id: String): Boolean {
        val all = list()
        if (all.none { it.id == id }) return false
        store.save(all.filter { it.id != id })
        return true
    }

    fun setDefault(id: String): RegistryResult<Unit> {
        val all = list()
        val target = all.firstOrNull { it.id == id }
            ?: return RegistryResult.Failure(RegistryError(RegistryErrorCode.NOT_FOUND, "Máy in không còn trên Master — chọn lại."))
        store.save(all.map { if (it.type == target.type) it.copy(isDefault = it.id == id) else it })
        return RegistryResult.Success(Unit)
    }

    /**
     * [printerId] tường minh: phải còn trong danh sách, KHÔNG âm thầm đổi sang máy khác.
     * Không chỉ định: máy mặc định của [type], không có thì máy đầu tiên của [type].
     */
    fun resolve(printerId: String?, type: PrinterRole): RegistryResult<PrinterProfile> {
        val all = list()
        if (printerId != null) {
            return all.firstOrNull { it.id == printerId }?.let { RegistryResult.Success(it) }
                ?: RegistryResult.Failure(RegistryError(RegistryErrorCode.NOT_FOUND, "Máy in không còn trên Master — chọn lại."))
        }
        val ofType = all.filter { it.type == type }
        return (ofType.firstOrNull { it.isDefault } ?: ofType.firstOrNull())?.let { RegistryResult.Success(it) }
            ?: RegistryResult.Failure(
                RegistryError(RegistryErrorCode.NOT_FOUND, "Chưa cấu hình máy in nào -- vào Cài đặt > Máy in & thiết bị trên Master, thêm máy in mạng.")
            )
    }

    private fun invalid(message: String): RegistryResult<PrinterProfile> =
        RegistryResult.Failure(RegistryError(RegistryErrorCode.INVALID_INPUT, message))

    /** Thông báo lỗi, hoặc null nếu hợp lệ. [others] = các máy khác (không gồm chính [candidate]). */
    private fun validate(candidate: PrinterProfile, others: List<PrinterProfile>): String? = when {
        candidate.name.isBlank() -> "Tên máy in không được để trống."
        others.any { it.name.equals(candidate.name, ignoreCase = true) } -> "Đã có máy in tên \"${candidate.name}\"."
        LanPrinterAddress.parse("${candidate.host}:${candidate.port}")?.host != candidate.host -> ADDRESS_ERROR
        else -> null
    }

    private companion object {
        const val ADDRESS_ERROR = "Địa chỉ máy in không hợp lệ (IP/tên máy và cổng 1–65535)."
    }
}

/** Dữ liệu form thêm/sửa máy in: cổng giữ dạng chữ để người dùng gõ dở vẫn hiển thị được. */
data class PrinterDraft(
    val id: String? = null,
    val name: String = "",
    val type: PrinterRole = PrinterRole.RECEIPT,
    val host: String = "",
    val portText: String = LanPrinterAddress.DEFAULT_PORT.toString(),
    val paperWidth: PaperWidth = PaperWidth.MM58,
) {
    companion object {
        fun blank() = PrinterDraft()
        fun from(config: PrinterProfile) = PrinterDraft(config.id, config.name, config.type, config.host, config.port.toString(), config.paperWidth)
    }
}
