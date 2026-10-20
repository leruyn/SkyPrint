package com.dcorp.skyprint.registry

import com.dcorp.skyprint.core.model.PaperWidth
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Nơi giữ danh sách máy in. Số lượng nhỏ nên lưu cả danh sách một lần. */
interface PrinterProfileStore {
    fun load(): List<PrinterProfile>
    fun save(list: List<PrinterProfile>)
}

class InMemoryPrinterProfileStore : PrinterProfileStore {
    private var list: List<PrinterProfile> = emptyList()
    override fun load(): List<PrinterProfile> = list
    override fun save(list: List<PrinterProfile>) { this.list = list }
}

/** Một chuỗi văn bản bền vững của nền tảng (SharedPreferences / NSUserDefaults / java.util.prefs). */
interface RawTextStore {
    fun read(): String?
    fun write(text: String)
}

/**
 * Danh sách lưu dạng JSON trong [raw]; dữ liệu hỏng/không đọc được coi như danh sách rỗng thay vì làm app lỗi.
 * Định dạng JSON GIỐNG HỆT bản cũ trong app (khoá `type`, `paperWidth` là tên enum) để máy đã lưu máy in vẫn đọc được sau khi nâng cấp.
 */
class JsonPrinterProfileStore(private val raw: RawTextStore) : PrinterProfileStore {
    @Serializable
    private class Row(
        val id: String,
        val name: String,
        val type: String = PrinterRole.RECEIPT.name,
        val host: String,
        val port: Int = LanPrinterAddress.DEFAULT_PORT,
        val paperWidth: String = PaperWidth.MM58.name,
        val isDefault: Boolean = false,
    )

    private val serializer = ListSerializer(Row.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): List<PrinterProfile> {
        val text = raw.read() ?: return emptyList()
        return try {
            json.decodeFromString(serializer, text).map {
                PrinterProfile(
                    id = it.id, name = it.name,
                    type = PrinterRole.entries.firstOrNull { r -> r.name == it.type } ?: PrinterRole.RECEIPT,
                    host = it.host, port = it.port,
                    paperWidth = PaperWidth.entries.firstOrNull { w -> w.name == it.paperWidth } ?: PaperWidth.MM58,
                    isDefault = it.isDefault,
                )
            }
        } catch (ignored: Exception) {
            emptyList()
        }
    }

    override fun save(list: List<PrinterProfile>) = raw.write(
        json.encodeToString(serializer, list.map { Row(it.id, it.name, it.type.name, it.host, it.port, it.paperWidth.name, it.isDefault) }),
    )
}
