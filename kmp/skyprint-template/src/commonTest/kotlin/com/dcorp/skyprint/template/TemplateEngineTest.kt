package com.dcorp.skyprint.template

import com.dcorp.skyprint.core.model.Align
import com.dcorp.skyprint.core.model.Element
import com.dcorp.skyprint.core.model.PaperWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TEST-UNIT-009 -- tích hợp Parser + Validator + Engine (REQ-009's req.md
 * acceptance criteria). Kịch bản mẫu hoá đơn rút gọn từ ví dụ trong
 * docs/research/pos-printing-business.md.
 */
class TemplateEngineTest {

    private val receiptSchema = TemplateSchema(
        documentType = "RECEIPT",
        paths = setOf(
            "store.name", "store.address",
            "order.table", "order.discount", "order.total",
            "items[].name", "items[].qty", "items[].amount",
            "items[].modifiers[].name",
            "print.isReprint", "print.count",
        ),
        sample = TemplateValue.Null,
    )

    private val receiptTemplateJson = """
    {
      "id": "receipt-default", "version": 1, "document": "RECEIPT", "paper": "MM80",
      "elements": [
        { "type": "text", "value": "{{store.name}}", "style": { "align": "CENTER", "bold": true, "size": 2 } },
        { "type": "text", "value": "*** BẢN IN LẠI lần {{print.count}} ***", "if": "print.isReprint", "style": { "align": "CENTER" } },
        { "type": "row", "columns": [ { "text": "Bàn: {{order.table}}" }, { "text": "" } ] },
        { "type": "divider" },
        { "type": "group", "each": "items", "as": "it", "elements": [
            { "type": "row", "columns": [
                { "text": "{{it.name}}", "weight": 6 },
                { "text": "{{it.qty}}", "weight": 1, "align": "RIGHT" },
                { "text": "{{it.amount | money}}", "weight": 3, "align": "RIGHT" }
            ] },
            { "type": "text", "value": "  + {{m.name}}", "each": "it.modifiers", "as": "m" }
        ]},
        { "type": "divider" },
        { "type": "row", "if": "order.discount > 0", "columns": [ { "text": "Giảm giá" }, { "text": "-{{order.discount | money}}", "align": "RIGHT" } ] },
        { "type": "row", "style": { "bold": true }, "columns": [ { "text": "TỔNG", "weight": 6 }, { "text": "{{order.total | money}}", "weight": 3, "align": "RIGHT" } ] },
        { "type": "cut" }
      ]
    }
    """.trimIndent()

    private fun sampleData(discount: Int = 0, reprint: Boolean = false) = TemplateValue.of(
        mapOf(
            "store" to mapOf("name" to "SKYPOS", "address" to "123 Lê Lợi"),
            "order" to mapOf("table" to "A12", "discount" to discount, "total" to 150000),
            "items" to listOf(
                mapOf(
                    "name" to "Phở bò", "qty" to 2, "amount" to 100000,
                    "modifiers" to listOf(mapOf("name" to "Không hành")),
                ),
                mapOf("name" to "Trà đá", "qty" to 1, "amount" to 50000, "modifiers" to emptyList<Any>()),
            ),
            "print" to mapOf("isReprint" to reprint, "count" to 2),
        ),
    )

    @Test
    fun `mau hop le voi schema khong co canh bao`() {
        val template = TemplateParser.parse(receiptTemplateJson)
        val issues = TemplateEngine.validate(template, receiptSchema)
        assertTrue(issues.isEmpty(), "không mong đợi issue nào, thực tế: $issues")
    }

    @Test
    fun `mau tham chieu bien khong ton tai bi validate chan`() {
        val bad = receiptTemplateJson.replace("{{order.total | money}}", "{{order.taxRate}}")
        val template = TemplateParser.parse(bad)
        val issues = TemplateEngine.validate(template, receiptSchema)
        assertTrue(issues.any { it.message.contains("order.taxRate") }, "thực tế: $issues")
    }

    @Test
    fun `render hoa don day du dung so tien mon va tong hien thi mon`() {
        val template = TemplateParser.parse(receiptTemplateJson)
        val result = TemplateEngine.render(template, sampleData())

        assertEquals(PaperWidth.MM80, result.document.paper)
        assertTrue(result.warnings.isEmpty(), "thực tế: ${result.warnings}")

        val texts = result.document.elements.filterIsInstance<Element.Text>().map { it.text }
        assertTrue(texts.any { it == "SKYPOS" })
        assertTrue(texts.none { it.contains("BẢN IN LẠI") }, "print.isReprint=false không được in dòng in lại")
        assertTrue(texts.any { it.contains("Không hành") }, "modifier phải xuất hiện")

        val rows = result.document.elements.filterIsInstance<Element.Row>()
        val itemRow = rows.first { r -> r.columns.first().text == "Phở bò" }
        assertEquals("2", itemRow.columns[1].text)
        assertEquals("1.000", itemRow.columns[2].text) // 100000 minor / 100 = 1000 -> "1.000"

        assertTrue(rows.none { it.columns.any { c -> c.text.contains("Giảm giá") } }, "discount=0 không in dòng giảm giá")

        val total = rows.first { r -> r.columns.first().text == "TỔNG" }
        assertEquals("1.500", total.columns.last().text)

        assertEquals(Element.Cut(true), result.document.elements.last())
    }

    @Test
    fun `dieu kien if bat dong dung theo du lieu`() {
        val template = TemplateParser.parse(receiptTemplateJson)
        val result = TemplateEngine.render(template, sampleData(discount = 20000, reprint = true))

        val texts = result.document.elements.filterIsInstance<Element.Text>().map { it.text }
        assertTrue(texts.any { it.contains("BẢN IN LẠI lần 2") })

        val rows = result.document.elements.filterIsInstance<Element.Row>()
        val discountRow = rows.first { it.columns.first().text == "Giảm giá" }
        assertEquals("-200", discountRow.columns.last().text)
    }

    @Test
    fun `bien thieu trong du lieu in chuoi rong khong huy lenh in`() {
        val template = TemplateParser.parse(
            """{"id":"t","version":1,"document":"RECEIPT","paper":"MM58","elements":[
                {"type":"text","value":"{{store.slogan}}"}
            ]}""",
        )
        val result = TemplateEngine.render(template, sampleData())
        assertEquals("", (result.document.elements.single() as Element.Text).text)
    }

    @Test
    fun `align khong hop le fallback LEFT kem canh bao`() {
        val template = TemplateParser.parse(
            """{"id":"t","version":1,"document":"RECEIPT","paper":"MM58","elements":[
                {"type":"text","value":"x","style":{"align":"MIDDLE"}}
            ]}""",
        )
        val result = TemplateEngine.render(template, sampleData())
        val text = result.document.elements.single() as Element.Text
        assertEquals(Align.LEFT, text.style.align)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `barcode sai du lieu bi bo qua kem canh bao khong crash render`() {
        val template = TemplateParser.parse(
            """{"id":"t","version":1,"document":"RECEIPT","paper":"MM58","elements":[
                {"type":"barcode","value":"{{order.table}}","barcodeType":"EAN13"},
                {"type":"cut"}
            ]}""",
        )
        val result = TemplateEngine.render(template, sampleData())
        assertEquals(listOf(Element.Cut(true)), result.document.elements)
        assertTrue(result.warnings.any { it.message.contains("EAN13", ignoreCase = true) || it.message.contains("chữ số") })
    }
}
