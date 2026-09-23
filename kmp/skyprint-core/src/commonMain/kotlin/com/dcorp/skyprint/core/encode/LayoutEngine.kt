package com.dcorp.skyprint.core.encode

import com.dcorp.skyprint.core.model.Align
import com.dcorp.skyprint.core.model.Column

/**
 * Layout thuần (không phụ thuộc ESC/POS) cho [com.dcorp.skyprint.core.model.Element.Row]
 * và [com.dcorp.skyprint.core.model.Element.Divider] (DESIGN-001). Quy tắc
 * cột theo req.md: cắt cột trái (nhãn) trước, KHÔNG BAO GIỜ cắt cột phải
 * (số tiền) -- nên cột `Align.RIGHT` luôn giữ nguyên độ dài text, phần bề
 * rộng còn lại mới chia theo weight cho các cột còn lại.
 */
internal object LayoutEngine {
    fun renderRow(columns: List<Column>, width: Int): String {
        val protectedIndices = columns.indices.filter { columns[it].align == Align.RIGHT }.toSet()
        val protectedWidth = protectedIndices.sumOf { columns[it].text.length }
        val remaining = (width - protectedWidth).coerceAtLeast(0)

        val flexibleIndices = columns.indices.filterNot { it in protectedIndices }
        val flexibleWidths = distributeByWeight(
            totalWidth = remaining,
            weights = flexibleIndices.map { columns[it].weight },
        )

        val widths = IntArray(columns.size)
        for (i in protectedIndices) widths[i] = columns[i].text.length
        flexibleIndices.forEachIndexed { idx, colIndex -> widths[colIndex] = flexibleWidths[idx] }

        return columns.indices.joinToString(separator = "") { i -> fit(columns[i].text, widths[i], columns[i].align) }
    }

    fun dividerLine(char: Char, width: Int): String = char.toString().repeat(width.coerceAtLeast(0))

    /** Largest-remainder method -- tổng các phần tử trả về luôn đúng bằng [totalWidth], không lệch do làm tròn. */
    private fun distributeByWeight(totalWidth: Int, weights: List<Int>): List<Int> {
        if (weights.isEmpty()) return emptyList()
        val weightSum = weights.sum()
        if (weightSum <= 0) return weights.map { 0 }

        val raw = weights.map { totalWidth.toDouble() * it / weightSum }
        val base = raw.map { it.toInt() }
        var remainder = totalWidth - base.sum()

        val order = raw.indices.sortedByDescending { raw[it] - base[it] }
        val result = base.toIntArray()
        for (i in order) {
            if (remainder <= 0) break
            result[i] += 1
            remainder -= 1
        }
        return result.toList()
    }

    /** Cắt nếu dài hơn [width] (luôn cắt, không thêm "…", để không lấn cột kế); đệm khoảng trắng theo [align] nếu ngắn hơn. */
    private fun fit(text: String, width: Int, align: Align): String {
        if (width <= 0) return ""
        if (text.length > width) return text.take(width)
        val pad = width - text.length
        return when (align) {
            Align.LEFT -> text + " ".repeat(pad)
            Align.RIGHT -> " ".repeat(pad) + text
            Align.CENTER -> {
                val left = pad / 2
                val right = pad - left
                " ".repeat(left) + text + " ".repeat(right)
            }
        }
    }
}
