package com.dcorp.skyprint.template

import com.dcorp.skyprint.core.model.Align
import com.dcorp.skyprint.core.model.BarcodeType
import com.dcorp.skyprint.core.model.Column
import com.dcorp.skyprint.core.model.Element
import com.dcorp.skyprint.core.model.MonoBitmap
import com.dcorp.skyprint.core.model.PaperWidth
import com.dcorp.skyprint.core.model.QrErrorCorrection
import com.dcorp.skyprint.core.model.ReceiptDocument
import com.dcorp.skyprint.core.model.TextStyle

/** App cấp bitmap cho `{{...}}` trong `Element.Image.source` -- engine không tự tải ảnh qua mạng (DESIGN-009). */
fun interface ImageResolver {
    fun resolve(source: String): MonoBitmap?
}

data class RenderOptions(
    val money: MoneyFormat = MoneyFormat.VND,
    val paper: PaperWidth? = null,
    val images: ImageResolver? = null,
)

data class RenderResult(val document: ReceiptDocument, val warnings: List<TemplateIssue>)

/**
 * Ghép [Template] (đã parse) với dữ liệu chứng từ thành [ReceiptDocument]
 * (REQ-009, DESIGN-009). [validate] chạy TRƯỚC lúc lưu mẫu (chặn biến sai
 * ngay, không đợi tới lúc in); [render] chạy MỖI LẦN in.
 */
object TemplateEngine {
    fun validate(template: Template, schema: TemplateSchema): List<TemplateIssue> =
        SchemaValidator.validate(template, schema)

    fun render(template: Template, data: TemplateValue, options: RenderOptions = RenderOptions()): RenderResult {
        val paper = options.paper
            ?: template.paper?.let { PaperWidth.entries.find { p -> p.name == it } }
            ?: throw TemplateException(TemplateErrorCode.INVALID_TEMPLATE, "Không xác định được khổ giấy (RenderOptions.paper hoặc Template.paper).")

        val warnings = mutableListOf<TemplateIssue>()
        val scope = Scope.root(data)
        val elements = Expander(options, warnings).expand(template.elements, scope, "")
        return RenderResult(ReceiptDocument(paper, elements), warnings)
    }
}

/** Duyệt cây [TemplateNode], xử lý `enabled`/`if`/`each`, sinh [Element] của skyprint-core. */
private class Expander(private val options: RenderOptions, private val warnings: MutableList<TemplateIssue>) {

    fun expand(nodes: List<TemplateNode>, scope: Scope, path: String): List<Element> {
        val result = mutableListOf<Element>()
        nodes.forEachIndexed { index, node -> result += expandNode(node, scope, "$path[$index:${node.type}]") }
        return result
    }

    private fun expandNode(node: TemplateNode, scope: Scope, path: String): List<Element> {
        if (!node.enabled) return emptyList()

        val each = node.each
        if (each != null) {
            val list = Interpolator.resolveValue(scope, each)
            val items = (list as? TemplateValue.Arr)?.items ?: run {
                if (list != TemplateValue.Null) warnings += TemplateIssue(path, "each='$each' không phải mảng, bỏ qua vòng lặp")
                emptyList()
            }
            val varName = node.asVar ?: "it"
            val out = mutableListOf<Element>()
            items.forEachIndexed { i, item ->
                val loopScope = scope.bind(
                    varName to item,
                    "\$index" to TemplateValue.Num(i.toDouble()),
                    "\$first" to TemplateValue.Bool(i == 0),
                    "\$last" to TemplateValue.Bool(i == items.lastIndex),
                )
                out += expandOnce(node, loopScope, "$path[$i]")
            }
            return out
        }

        return expandOnce(node, scope, path)
    }

    private fun expandOnce(node: TemplateNode, scope: Scope, path: String): List<Element> {
        node.ifExpr?.let { if (!ConditionEvaluator.evaluate(scope, it)) return emptyList() }

        return try {
            when (node.type) {
                "group" -> expand(node.elements.orEmpty(), scope, path)
                "text" -> listOf(buildText(node, scope))
                "row" -> listOf(buildRow(node, scope))
                "divider" -> listOf(Element.Divider(node.char.firstOrNull() ?: '-'))
                "feed" -> listOf(Element.Feed(node.lines))
                "cut" -> listOf(Element.Cut(node.partial))
                "qr" -> buildQr(node, scope)
                "barcode" -> listOf(buildBarcode(node, scope))
                "drawer" -> listOf(Element.Drawer(node.pin, node.pulseMs))
                "beep" -> listOf(Element.Beep(node.times, node.durationMs))
                "image" -> buildImage(node, scope, path)
                else -> {
                    warnings += TemplateIssue(path, "Loại phần tử không tồn tại: '${node.type}'")
                    emptyList()
                }
            }
        } catch (e: IllegalArgumentException) {
            // Dữ liệu sau nội suy không hợp lệ cho model (vd Barcode sai độ dài) --
            // bỏ qua đúng 1 phần tử này, KHÔNG huỷ cả lệnh in (req.md's alternate flow).
            warnings += TemplateIssue(path, "Bỏ qua vì dữ liệu không hợp lệ: ${e.message}")
            emptyList()
        }
    }

    private fun text(scope: Scope, raw: String) = Interpolator.interpolate(scope, raw, options.money)

    private fun buildText(node: TemplateNode, scope: Scope): Element.Text {
        val style = node.style ?: TemplateStyle()
        return Element.Text(
            text = text(scope, node.value.orEmpty()),
            style = TextStyle(
                align = align(style.align),
                bold = style.bold,
                width = style.width,
                height = style.height,
                inverse = style.inverse,
                underline = style.underline,
            ),
        )
    }

    private fun buildRow(node: TemplateNode, scope: Scope): Element.Row = Element.Row(
        node.columns.orEmpty().map { col ->
            Column(text = text(scope, col.text), weight = col.weight, align = align(col.align), bold = col.bold)
        },
    )

    private fun buildQr(node: TemplateNode, scope: Scope): List<Element> {
        val qr = Element.QrCode(
            data = text(scope, node.value.orEmpty()),
            moduleSize = node.moduleSize,
            errorCorrection = errorCorrection(node.errorCorrection),
            align = align(node.align),
        )
        val captionText = node.caption?.let { text(scope, it) }
        return if (captionText.isNullOrEmpty()) listOf(qr)
        else listOf(qr, Element.Text(captionText, TextStyle(align = align(node.align))))
    }

    private fun buildBarcode(node: TemplateNode, scope: Scope): Element.Barcode = Element.Barcode(
        data = text(scope, node.value.orEmpty()),
        type = barcodeType(node.barcodeType),
        heightDots = node.heightDots,
        hri = node.hri,
        align = align(node.align),
    )

    private fun buildImage(node: TemplateNode, scope: Scope, path: String): List<Element> {
        val source = text(scope, node.value.orEmpty())
        val bitmap = options.images?.resolve(source)
        if (bitmap == null) {
            warnings += TemplateIssue(path, "Image: không resolve được nguồn ảnh '$source', đã bỏ qua.")
            return emptyList()
        }
        return listOf(Element.Image(bitmap, align(node.align)))
    }

    private fun align(raw: String): Align = when (raw.uppercase()) {
        "LEFT" -> Align.LEFT
        "RIGHT" -> Align.RIGHT
        "CENTER" -> Align.CENTER
        else -> { warnings += TemplateIssue("", "Align không hợp lệ '$raw', dùng LEFT"); Align.LEFT }
    }

    private fun errorCorrection(raw: String): QrErrorCorrection = when (raw.uppercase()) {
        "L" -> QrErrorCorrection.L
        "M" -> QrErrorCorrection.M
        "Q" -> QrErrorCorrection.Q
        "H" -> QrErrorCorrection.H
        else -> { warnings += TemplateIssue("", "QR errorCorrection không hợp lệ '$raw', dùng M"); QrErrorCorrection.M }
    }

    private fun barcodeType(raw: String): BarcodeType =
        BarcodeType.entries.find { it.name == raw.uppercase() }
            ?: run { warnings += TemplateIssue("", "Barcode.type không hợp lệ '$raw', dùng CODE128"); BarcodeType.CODE128 }
}
