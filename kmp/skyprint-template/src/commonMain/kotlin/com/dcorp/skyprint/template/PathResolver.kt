package com.dcorp.skyprint.template

/**
 * Ngữ cảnh resolve path (DESIGN-009) -- [bindings] là biến vòng lặp
 * (`it`, `m`, `$index`, `$first`, `$last`...) che (shadow) field cùng tên
 * ở [root] khi segment đầu của path trùng tên biến; path không khớp biến
 * nào thì resolve tiếp từ [root].
 */
class Scope private constructor(val root: TemplateValue, private val bindings: Map<String, TemplateValue>) {
    fun bind(vararg pairs: Pair<String, TemplateValue>): Scope = Scope(root, bindings + pairs)

    internal fun lookupBinding(name: String): TemplateValue? = bindings[name]

    companion object {
        fun root(data: TemplateValue): Scope = Scope(data, emptyMap())
    }
}

/**
 * Đọc giá trị theo path kiểu `a.b.c`, `items[0].name`, hoặc biến vòng lặp
 * `it.name`/`$index` (DESIGN-009). Không throw khi thiếu -- trả [TemplateValue.Null],
 * đúng "Alternate flow" của req.md ("biến thiếu trong dữ liệu -> in chuỗi
 * rỗng/cảnh báo, không huỷ lệnh in").
 */
object PathResolver {
    private val SEGMENT_REGEX = Regex("""([^.\[\]]+)|(\[(\d+)\])""")

    fun resolve(scope: Scope, path: String): TemplateValue {
        val segments = tokenize(path)
        if (segments.isEmpty()) return TemplateValue.Null

        val first = segments.first()
        var current: TemplateValue = when (first) {
            is Segment.Name -> scope.lookupBinding(first.name) ?: fieldOf(scope.root, first.name)
            is Segment.Index -> TemplateValue.Null // path không hợp lệ nếu bắt đầu bằng index, coi như rỗng
        }

        for (segment in segments.drop(1)) {
            current = when (segment) {
                is Segment.Name -> fieldOf(current, segment.name)
                is Segment.Index -> indexOf(current, segment.index)
            }
        }
        return current
    }

    /** Tách path thành segment tên/chỉ số, giữ nguyên thứ tự xuất hiện (vd `it.modifiers[0].name` -> it, modifiers, [0], name). */
    private fun tokenize(path: String): List<Segment> {
        val segments = mutableListOf<Segment>()
        for (match in SEGMENT_REGEX.findAll(path)) {
            val indexGroup = match.groups[3]
            if (indexGroup != null) segments += Segment.Index(indexGroup.value.toInt())
            else segments += Segment.Name(match.groupValues[1])
        }
        return segments
    }

    private fun fieldOf(value: TemplateValue, name: String): TemplateValue =
        (value as? TemplateValue.Obj)?.fields?.get(name) ?: TemplateValue.Null

    private fun indexOf(value: TemplateValue, index: Int): TemplateValue =
        (value as? TemplateValue.Arr)?.items?.getOrNull(index) ?: TemplateValue.Null

    private sealed interface Segment {
        data class Name(val name: String) : Segment
        data class Index(val index: Int) : Segment
    }
}
