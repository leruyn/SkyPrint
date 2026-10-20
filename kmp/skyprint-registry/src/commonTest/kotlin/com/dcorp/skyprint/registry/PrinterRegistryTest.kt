package com.dcorp.skyprint.registry

import com.dcorp.skyprint.core.model.PaperWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UNIT-052-01..05, 08: validator, registry (CRUD, mặc định, persist, migration), resolver. */
class PrinterRegistryTest {
    private var seq = 0
    private fun registry(store: PrinterProfileStore = InMemoryPrinterProfileStore()) = PrinterRegistry(store) { "id-${++seq}" }

    private fun PrinterRegistry.addOk(name: String, type: PrinterRole = PrinterRole.RECEIPT, host: String = "192.168.2.113", port: Int = 9100, paper: PaperWidth = PaperWidth.MM58): PrinterProfile =
        (add(name, type, host, port, paper) as RegistryResult.Success<PrinterProfile>).value

    private fun errorOf(r: RegistryResult<*>) = (r as RegistryResult.Failure).error

    // UNIT-052-01
    @Test
    fun `validator tu choi ten rong ten trung host va port sai`() {
        val r = registry()
        r.addOk("Quầy")
        assertEquals(RegistryErrorCode.INVALID_INPUT, errorOf(r.add("  ", PrinterRole.RECEIPT, "1.2.3.4", 9100, PaperWidth.MM58)).code)
        assertEquals(RegistryErrorCode.INVALID_INPUT, errorOf(r.add("quầy", PrinterRole.BAR, "1.2.3.4", 9100, PaperWidth.MM58)).code) // trùng, không phân biệt hoa thường
        assertEquals(RegistryErrorCode.INVALID_INPUT, errorOf(r.add("Bếp", PrinterRole.KITCHEN, "", 9100, PaperWidth.MM58)).code)
        assertEquals(RegistryErrorCode.INVALID_INPUT, errorOf(r.add("Bếp", PrinterRole.KITCHEN, "1.2 .3.4", 9100, PaperWidth.MM58)).code)
        assertEquals(RegistryErrorCode.INVALID_INPUT, errorOf(r.add("Bếp", PrinterRole.KITCHEN, "1.2.3.4", 0, PaperWidth.MM58)).code)
        assertEquals(RegistryErrorCode.INVALID_INPUT, errorOf(r.add("Bếp", PrinterRole.KITCHEN, "1.2.3.4", 70000, PaperWidth.MM58)).code)
        assertEquals(1, r.list().size)
    }

    // UNIT-052-02
    @Test
    fun `them sua xoa va id duy nhat`() {
        val r = registry()
        val a = r.addOk("Quầy")
        val b = r.addOk("Bếp", PrinterRole.KITCHEN, "192.168.2.114")
        assertNotEquals(a.id, b.id)

        val updated = (r.update(a.copy(name = "Quầy 1", paperWidth = PaperWidth.MM80, host = "10.0.0.5")) as RegistryResult.Success<PrinterProfile>).value
        assertEquals("Quầy 1", r.list().first { it.id == a.id }.name)
        assertEquals(PaperWidth.MM80, updated.paperWidth)
        assertEquals("10.0.0.5:9100", updated.address)

        assertTrue(r.delete(b.id))
        assertEquals(listOf(a.id), r.list().map { it.id })
        assertEquals(false, r.delete("khong-co"))
    }

    @Test
    fun `sua giu nguyen ten cua chinh no khong bi coi la trung doi sang ten may khac thi bi tu choi`() {
        val r = registry()
        val a = r.addOk("Quầy")
        val b = r.addOk("Bếp", PrinterRole.KITCHEN)
        assertTrue(r.update(a.copy(host = "10.0.0.9")) is RegistryResult.Success)
        assertEquals(RegistryErrorCode.INVALID_INPUT, errorOf(r.update(b.copy(name = "QUẦY"))).code)
        assertEquals(RegistryErrorCode.NOT_FOUND, errorOf(r.update(a.copy(id = "khong-co"))).code)
    }

    // UNIT-052-03
    @Test
    fun `may dau tien cua moi loai tu thanh mac dinh dat mac dinh chi con mot may moi loai`() {
        val r = registry()
        val q1 = r.addOk("Quầy 1")
        val q2 = r.addOk("Quầy 2", host = "192.168.2.120")
        val bep = r.addOk("Bếp", PrinterRole.KITCHEN, "192.168.2.114")
        assertTrue(q1.isDefault); assertTrue(!q2.isDefault); assertTrue(bep.isDefault)

        assertTrue(r.setDefault(q2.id) is RegistryResult.Success)
        val byId = r.list().associateBy { it.id }
        assertTrue(byId.getValue(q2.id).isDefault)
        assertTrue(!byId.getValue(q1.id).isDefault)
        assertTrue(byId.getValue(bep.id).isDefault) // loại khác không bị động tới
        assertEquals(RegistryErrorCode.NOT_FOUND, errorOf(r.setDefault("khong-co")).code)
    }

    @Test
    fun `xoa may mac dinh thi khong tu chon may khac lam mac dinh`() {
        val r = registry()
        val q1 = r.addOk("Quầy 1")
        r.addOk("Quầy 2", host = "192.168.2.120")
        r.delete(q1.id)
        assertTrue(r.list().none { it.isDefault })
    }

    // UNIT-052-04
    @Test
    fun `luu roi nap lai tu store giong ban goc`() {
        val store = InMemoryPrinterProfileStore()
        val first = registry(store)
        first.addOk("Quầy", paper = PaperWidth.MM80)
        first.addOk("Bếp", PrinterRole.KITCHEN, "192.168.2.114", 9101)
        val reloaded = PrinterRegistry(store) { "x" }
        assertEquals(first.list(), reloaded.list())
    }

    @Test
    fun `JsonPrinterProfileStore doc ghi qua text va chiu duoc du lieu hong`() {
        val raw = object : RawTextStore {
            var text: String? = null
            override fun read() = text
            override fun write(text: String) { this.text = text }
        }
        val store = JsonPrinterProfileStore(raw)
        assertEquals(emptyList(), store.load())
        val list = listOf(PrinterProfile("a", "Quầy", PrinterRole.RECEIPT, "1.2.3.4", 9100, PaperWidth.MM80, true))
        store.save(list)
        assertEquals(list, store.load())
        raw.text = "{khong phai json"
        assertEquals(emptyList(), store.load())
    }

    // UNIT-052-08
    @Test
    fun `resolve theo id theo mac dinh cua loai may dau tien cua loai hoac NOT_FOUND`() {
        val r = registry()
        assertEquals(RegistryErrorCode.NOT_FOUND, errorOf(r.resolve(null, PrinterRole.RECEIPT)).code)

        val q1 = r.addOk("Quầy 1")
        val q2 = r.addOk("Quầy 2", host = "192.168.2.120")
        val bep = r.addOk("Bếp", PrinterRole.KITCHEN, "192.168.2.114")
        assertEquals(q2.id, (r.resolve(q2.id, PrinterRole.RECEIPT) as RegistryResult.Success<PrinterProfile>).value.id)
        assertEquals(bep.id, (r.resolve(bep.id, PrinterRole.RECEIPT) as RegistryResult.Success<PrinterProfile>).value.id) // id tường minh thắng loại
        assertEquals(q1.id, (r.resolve(null, PrinterRole.RECEIPT) as RegistryResult.Success<PrinterProfile>).value.id)   // mặc định

        r.setDefault(q2.id)
        assertEquals(q2.id, (r.resolve(null, PrinterRole.RECEIPT) as RegistryResult.Success<PrinterProfile>).value.id)

        r.delete(q2.id)  // xoá mặc định -> lấy máy đầu tiên của loại
        assertEquals(q1.id, (r.resolve(null, PrinterRole.RECEIPT) as RegistryResult.Success<PrinterProfile>).value.id)

        val gone = errorOf(r.resolve("da-xoa", PrinterRole.RECEIPT))
        assertEquals(RegistryErrorCode.NOT_FOUND, gone.code)
        assertTrue(gone.message.contains("chọn lại"), gone.message)
    }

    // UNIT-052-10 (phần logic của form thêm/sửa)
    @Test
    fun `save tu form - khong co id la them moi co id la sua cong nhap chu bi tu choi`() {
        val r = registry()
        val created = (r.save(PrinterDraft(name = " Quầy ", type = PrinterRole.RECEIPT, host = " 192.168.2.113 ", portText = "9100", paperWidth = PaperWidth.MM80)) as RegistryResult.Success<PrinterProfile>).value
        assertEquals("Quầy", created.name); assertEquals("192.168.2.113", created.host); assertEquals(PaperWidth.MM80, created.paperWidth)

        val edited = (r.save(PrinterDraft(id = created.id, name = "Quầy 1", type = PrinterRole.RECEIPT, host = "10.0.0.8", portText = "9101", paperWidth = PaperWidth.MM58)) as RegistryResult.Success<PrinterProfile>).value
        assertEquals(created.id, edited.id); assertEquals("10.0.0.8:9101", edited.address)
        assertEquals(1, r.list().size)

        for (bad in listOf("", "abc", "0", "65536", "91 00")) {
            val e = errorOf(r.save(PrinterDraft(name = "X$bad", type = PrinterRole.BAR, host = "1.2.3.4", portText = bad, paperWidth = PaperWidth.MM58)))
            assertEquals(RegistryErrorCode.INVALID_INPUT, e.code, "port '$bad'")
        }
        assertEquals(1, r.list().size)
    }

    @Test
    fun `PrinterDraft from config de sua va port mac dinh khi form moi`() {
        val cfg = PrinterProfile("id1", "Bếp", PrinterRole.KITCHEN, "1.2.3.4", 9101, PaperWidth.MM80, true)
        val d = PrinterDraft.from(cfg)
        assertEquals("id1", d.id); assertEquals("9101", d.portText); assertEquals(PaperWidth.MM80, d.paperWidth)
        assertEquals("9100", PrinterDraft.blank().portText)
        assertEquals(PaperWidth.MM58, PrinterDraft.blank().paperWidth)
    }

    @Test
    fun `doc duoc JSON da luu boi ban cu trong app`() {
        val raw = object : RawTextStore {
            override fun read() = """[{"id":"a","name":"Quầy","host":"1.2.3.4","paperWidth":"MM80","isDefault":true},{"id":"b","name":"Bếp","type":"KITCHEN","host":"1.2.3.5","port":9101}]"""
            override fun write(text: String) = Unit
        }
        val list = JsonPrinterProfileStore(raw).load()
        assertEquals(PrinterProfile("a", "Quầy", PrinterRole.RECEIPT, "1.2.3.4", 9100, PaperWidth.MM80, true), list[0])
        assertEquals(PrinterProfile("b", "Bếp", PrinterRole.KITCHEN, "1.2.3.5", 9101, PaperWidth.MM58, false), list[1])
    }

    @Test
    fun `JSON ghi ra dung khoa cu de may cu doc lai duoc`() {
        var saved = ""
        val raw = object : RawTextStore {
            override fun read() = saved.ifEmpty { null }
            override fun write(text: String) { saved = text }
        }
        JsonPrinterProfileStore(raw).save(listOf(PrinterProfile("a", "Quầy", PrinterRole.KITCHEN, "1.2.3.4", 9101, PaperWidth.MM80, true)))
        assertTrue("\"type\":\"KITCHEN\"" in saved && "\"paperWidth\":\"MM80\"" in saved && "\"isDefault\":true" in saved, saved)
    }
}
