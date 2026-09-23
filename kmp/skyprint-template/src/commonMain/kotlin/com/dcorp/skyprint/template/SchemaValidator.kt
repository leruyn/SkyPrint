package com.dcorp.skyprint.template

/**
 * Kiểm mọi path tham chiếu trong [Template] có thuộc [TemplateSchema.paths]
 * không -- chạy lúc LƯU mẫu (req.md: "chặn ngay lúc Lưu, báo đúng tên biến"),
 * không đợi tới lúc in mới phát hiện thiếu/sai tên biến.
 *
 * Biến vòng lặp (`it`, `as`-alias, `$index`/`$first`/`$last`) không kiểm sâu
 * ở v1 -- schema không mô tả kiểu phần tử mảng, chỉ kiểm path đó THUỘC một
 * `each` hợp lệ (chính `each` vẫn được kiểm như path bình thường).
 */
object SchemaValidator {
    private val PLACEHOLDER_PATH = Regex("""\{\{\s*([^|}\s]+)""")
    private val ARRAY_INDEX = Regex("""\[\d+]""")
    private val TERM_PATH = Regex("""(?:^|&&|\|\|)\s*!?\s*([^\s!=<>&|]+)""")

    fun validate(template: Template, schema: TemplateSchema): List<TemplateIssue> {
        val issues = mutableListOf<TemplateIssue>()
        walk(template.elements, emptySet(), "", schema, issues)
        return issues
    }

    private fun walk(nodes: List<TemplateNode>, boundVars: Set<String>, prefix: String, schema: TemplateSchema, issues: MutableList<TemplateIssue>) {
        nodes.forEachIndexed { index, node ->
            val here = "$prefix[$index:${node.type}]"

            // `each`/`as` trên chính node này (kể cả node lá, không chỉ "group") áp dụng
            // cho NỘI DUNG của chính nó (nó lặp lại chính nó) -- nên selfBound dùng để
            // kiểm value/if/columns của CHÍNH node, không phải boundVars gốc.
            val selfBound = if (node.each != null) boundVars + setOfNotNull(node.asVar ?: "it", "\$index", "\$first", "\$last") else boundVars

            node.each?.let { checkArrayPath(it, boundVars, here, schema, issues) } // each tự nó resolve ở scope NGOÀI, không phải selfBound
            node.ifExpr?.let { extractConditionPaths(it).forEach { p -> checkPath(p, selfBound, here, schema, issues) } }
            node.value?.let { extractPlaceholderPaths(it).forEach { p -> checkPath(p, selfBound, here, schema, issues) } }
            node.caption?.let { extractPlaceholderPaths(it).forEach { p -> checkPath(p, selfBound, here, schema, issues) } }
            node.columns?.forEach { col -> extractPlaceholderPaths(col.text).forEach { p -> checkPath(p, selfBound, here, schema, issues) } }

            node.elements?.let { walk(it, selfBound, here, schema, issues) }
        }
    }

    private fun checkPath(path: String, boundVars: Set<String>, where: String, schema: TemplateSchema, issues: MutableList<TemplateIssue>) {
        val first = path.substringBefore('.').substringBefore('[')
        if (first in boundVars) return // biến vòng lặp -- không kiểm sâu ở v1, xem doc đầu file
        val normalized = path.replace(ARRAY_INDEX, "[]")
        if (normalized !in schema.paths) {
            issues += TemplateIssue(where, "Biến không tồn tại trong schema '${schema.documentType}': '$path'")
        }
    }

    /** [each] trỏ tới CHÍNH mảng (vd `items`), còn schema.paths khai theo field của phần tử (`items[].name`) -- hợp lệ nếu có bất kỳ path nào trong schema bắt đầu bằng `<normalized>[]`. */
    private fun checkArrayPath(path: String, boundVars: Set<String>, where: String, schema: TemplateSchema, issues: MutableList<TemplateIssue>) {
        val first = path.substringBefore('.').substringBefore('[')
        if (first in boundVars) return
        val normalized = path.replace(ARRAY_INDEX, "[]")
        val prefix = "$normalized[]"
        val valid = normalized in schema.paths || schema.paths.any { it.startsWith(prefix) }
        if (!valid) {
            issues += TemplateIssue(where, "each: mảng không tồn tại trong schema '${schema.documentType}': '$path'")
        }
    }

    private fun extractPlaceholderPaths(text: String): List<String> = PLACEHOLDER_PATH.findAll(text).map { it.groupValues[1] }.toList()

    private fun extractConditionPaths(expr: String): List<String> = TERM_PATH.findAll(expr).map { it.groupValues[1] }.toList()
}
