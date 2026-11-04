package com.dcorp.skyprint.raster

/** Kiểu chữ: cỡ tính bằng điểm ảnh (đã theo bề rộng ảnh), đậm hay thường. */
data class TextStyle(val sizePx: Float, val bold: Boolean = false)

/** Đo chữ theo nền tảng -- đây là phần DUY NHẤT của việc dàn trang mà engine không tự làm được. */
interface TextMeasurer {
    fun width(text: String, style: TextStyle): Int
    fun lineHeight(style: TextStyle): Int
    /** Ngắt [text] thành các dòng, mỗi dòng rộng không quá [maxWidth] (ngắt theo từ, từ quá dài thì cắt theo ký tự). */
    fun wrap(text: String, style: TextStyle, maxWidth: Int): List<String>
}

/** Bề mặt vẽ theo nền tảng: chỉ cần vẽ chữ, vẽ đường kẻ ngang và xuất ảnh xám. */
interface DrawSurface {
    /** [y] là mép TRÊN của dòng chữ (engine đã cộng chiều cao dòng). */
    fun drawText(text: String, x: Int, y: Int, style: TextStyle)
    fun drawHLine(y: Int, x1: Int, x2: Int, thickness: Int, dashed: Boolean)
    fun toGray(): GrayBitmap
}

interface GraphicsBackend {
    val measurer: TextMeasurer
    fun createSurface(width: Int, height: Int): DrawSurface
}

sealed interface DrawOp {
    data class Text(val text: String, val x: Int, val y: Int, val style: TextStyle) : DrawOp
    data class HLine(val y: Int, val x1: Int, val x2: Int, val thickness: Int, val dashed: Boolean) : DrawOp
}

class ReceiptLayout2(val ops: List<DrawOp>, val height: Int)

/**
 * Dàn trang [ReceiptDocument] thành danh sách thao tác vẽ có toạ độ (REQ-053). Thuần Kotlin -- test được bằng bộ đo giả;
 * cỡ chữ tỉ lệ theo bề rộng ảnh nên cùng một bố cục cho mọi nền tảng. Cột bảng món theo tỉ lệ 5:1:2:3 như Android.
 */
class ReceiptLayoutEngine(private val m: TextMeasurer) {
    fun layout(doc: ReceiptDocument, width: Int): ReceiptLayout2 {
        val body = TextStyle(width / 21f)
        val bold = body.copy(bold = true)
        val title = TextStyle(body.sizePx * 1.7f, bold = true)
        val emphasis = TextStyle(body.sizePx * 1.25f, bold = true)
        val contentW = width - 2 * PAD
        val right = width - PAD
        val ops = ArrayList<DrawOp>()
        var y = if (doc.blocks.isEmpty()) 0 else PAD

        fun lineGap(style: TextStyle) = (style.sizePx * 0.12f).toInt().coerceAtLeast(2)
        fun style(b: ReceiptBlock.Text) = if (b.scale >= 2) title else if (b.bold) bold else body

        for (block in doc.blocks) when (block) {
            is ReceiptBlock.Text -> {
                val st = style(block)
                for (line in m.wrap(block.text, st, contentW)) {
                    val lw = m.width(line, st)
                    val x = when (block.align) {
                        ReceiptAlign.LEFT -> PAD
                        ReceiptAlign.CENTER -> ((width - lw) / 2).coerceAtLeast(0)
                        ReceiptAlign.RIGHT -> (right - lw).coerceAtLeast(0)
                    }
                    ops += DrawOp.Text(line, x, y, st)
                    y += m.lineHeight(st) + lineGap(st)
                }
            }
            ReceiptBlock.Separator -> {
                y += SEP_GAP
                ops += DrawOp.HLine(y, PAD, right, 2, dashed = true)
                y += SEP_GAP
            }
            is ReceiptBlock.KeyValue -> {
                val st = if (block.emphasis) emphasis else body
                val vw = m.width(block.value, st)
                val labels = m.wrap(block.label, st, (contentW - vw - GAP).coerceAtLeast(st.sizePx.toInt()))
                labels.forEachIndexed { i, l -> ops += DrawOp.Text(l, PAD, y + i * (m.lineHeight(st) + lineGap(st)), st) }
                ops += DrawOp.Text(block.value, right - vw, y, st)
                y += labels.size * (m.lineHeight(st) + lineGap(st)) + if (block.emphasis) lineGap(st) * 2 else 0
            }
            is ReceiptBlock.Columns -> y = row(ops, y, block.cells, block.weights, if (block.bold) bold else body, contentW, right, ::lineGap)
            is ReceiptBlock.DishRow -> y = row(
                ops, y, listOf(block.name, block.qty, block.unit, block.amount), ReceiptLayout.DISH_WEIGHTS, body, contentW, right, ::lineGap,
            )
            is ReceiptBlock.Adornment -> {
                val indent = if (block.kind == AdornmentKind.DISCOUNT) INDENT_DISCOUNT else INDENT_MODIFIER
                val prefix = if (block.kind == AdornmentKind.DISCOUNT) "− " else "· "
                val amountW = block.amount?.let { m.width(it, body) } ?: 0
                val lines = m.wrap(prefix + block.text, body, (contentW - indent - amountW - GAP).coerceAtLeast(body.sizePx.toInt()))
                lines.forEachIndexed { i, l -> ops += DrawOp.Text(l, PAD + indent, y + i * (m.lineHeight(body) + lineGap(body)), body) }
                block.amount?.let { ops += DrawOp.Text(it, right - amountW, y, body) }
                y += lines.size * (m.lineHeight(body) + lineGap(body))
            }
            ReceiptBlock.Spacer -> y += (body.sizePx / 2).toInt()
        }
        if (doc.blocks.isNotEmpty()) y += PAD * 3
        return ReceiptLayout2(ops, y)
    }

    /** Hàng nhiều cột: ô đầu căn trái và được xuống dòng, các ô sau căn phải ở dòng đầu. Trả về y mới. */
    private fun row(
        ops: MutableList<DrawOp>, y: Int, cells: List<String>, weights: FloatArray, st: TextStyle,
        contentW: Int, right: Int, lineGap: (TextStyle) -> Int,
    ): Int {
        val avail = contentW - GAP * (cells.size - 1)
        val total = weights.take(cells.size).sum()
        val colW = IntArray(cells.size) { (avail * weights[it] / total).toInt() }
        // x trái của từng cột
        val colX = IntArray(cells.size)
        var x = PAD
        for (i in cells.indices) { colX[i] = x; x += colW[i] + GAP }
        val nameLines = m.wrap(cells[0], st, colW[0])
        nameLines.forEachIndexed { i, l -> ops += DrawOp.Text(l, colX[0], y + i * (m.lineHeight(st) + lineGap(st)), st) }
        for (i in 1 until cells.size) {
            val cw = m.width(cells[i], st)
            val colRight = if (i == cells.size - 1) right else colX[i] + colW[i]
            ops += DrawOp.Text(cells[i], colRight - cw, y, st)
        }
        return y + nameLines.size * (m.lineHeight(st) + lineGap(st))
    }

    companion object {
        const val PAD = 6
        private const val GAP = 12
        private const val SEP_GAP = 6
        private const val INDENT_DISCOUNT = 14
        private const val INDENT_MODIFIER = 28
    }
}

/** [ReceiptRasterizer] dựng từ [GraphicsBackend]: dàn trang một lần, tạo ảnh đúng chiều cao rồi vẽ từng thao tác. */
class GraphicsReceiptRasterizer(private val backend: GraphicsBackend) : ReceiptRasterizer {
    private val engine = ReceiptLayoutEngine(backend.measurer)

    override fun render(doc: ReceiptDocument, widthDots: Int): GrayBitmap {
        val layout = engine.layout(doc, widthDots)
        val surface = backend.createSurface(widthDots, maxOf(layout.height, 1))
        for (op in layout.ops) when (op) {
            is DrawOp.Text -> surface.drawText(op.text, op.x, op.y, op.style)
            is DrawOp.HLine -> surface.drawHLine(op.y, op.x1, op.x2, op.thickness, op.dashed)
        }
        return surface.toGray()
    }
}
