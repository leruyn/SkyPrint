# DESIGN-009 — Template engine (DESIGN)

> depends_on REQ-009, DESIGN-001 (output là `ReceiptDocument`), DESIGN-010 (element mở rộng).

## Interfaces

KMP (module mới `skyprint-template`, package `com.dcorp.skyprint.template`):

```kotlin
object TemplateParser { fun parse(json: String): Template }                         // ném TemplateException(INVALID_TEMPLATE, path)
object TemplateEngine {
  fun validate(template: Template, schema: TemplateSchema): List<TemplateIssue>
  fun render(template: Template, data: TemplateValue, options: RenderOptions = RenderOptions()): RenderResult
}
sealed interface TemplateValue { /* Str, Num(BigDecimal-like Long+scale), Bool, List, Obj, Null */
  companion object { fun fromJson(json: String): TemplateValue; fun of(map: Map<String, Any?>): TemplateValue } }
data class RenderOptions(val locale: MoneyFormat = MoneyFormat.VND, val paper: PaperWidth? = null /* ghi đè mẫu */,
                         val capabilities: PrinterCapabilities = PrinterCapabilities.ALL, val images: ImageResolver? = null)
data class RenderResult(val document: ReceiptDocument, val warnings: List<TemplateIssue>)
data class TemplateSchema(val documentType: String, val paths: Set<String> /* "order.total", "items[].name" */, val sample: TemplateValue)
fun interface ImageResolver { fun resolve(source: String): MonoBitmap? }            // logo: app cấp bitmap, engine không tự tải mạng
```

Dart (`skyprint_core/lib/src/template/`): cùng tên; `TemplateValue` = `Object?` (Map/List/num/String/bool) — giữ API đơn giản cho Flutter.

## Components

- `TemplateParser` — JSON → cây `TemplateElement` (text, row, divider, feed, cut, qr, barcode, image, drawer, beep, group). Thuộc tính chung: `if`, `enabled`, `each` + `as`.
- `Interpolator` — tách `{{ path | fmt:'arg' | fmt2 }}`; path hỗ trợ `a.b.c`, `items[0].name`, biến vòng lặp (`it.name`), `$index`, `$first`, `$last`.
- `ConditionEvaluator` — ngữ pháp tối giản, không có hàm/script:
  `cond := term (('&&'|'||') term)*`, `term := '!'? path (op literal)?`, `op := == != > >= < <=`, literal = số / `'chuỗi'` / true / false / null. Truthy: không null, không rỗng, ≠ 0, ≠ false.
- `Formatters` (đăng ký cố định, cùng tập ở KMP/Dart): `money` (chia `minorUnits`, ngăn nghìn `.`), `number:n`, `datetime:'pattern'` (ISO-8601 in → pattern con: `dd MM yyyy HH mm ss`), `upper`, `lower`, `default:'x'`, `truncate:n`, `pad:n`.
- `Expander` — duyệt cây, mở rộng `group/each` → danh sách `Element` phẳng của DESIGN-001; lọc theo `capabilities`.
- `SchemaValidator` — mọi path trong mẫu (sau khi thay biến vòng lặp thành `items[]`) phải thuộc `schema.paths`.

## Data model

- Định dạng mẫu: xem ví dụ đầy đủ ở docs/research/pos-printing-business.md §3.3. Trường gốc: `id`, `version` (số nguyên, mẫu), `schemaVersion` (định dạng — v1), `document`, `paper`, `elements`.
- Golden: `spec/golden/templates/<case>.template.json` + `<case>.data.json` → `<case>.document.json` (ReceiptDocument dạng JSON) — kiểm engine độc lập với encoder.
- Mẫu mặc định: `spec/templates/{receipt,precheck,kitchen,kitchen-void,shift-z}.{mm58,mm80}.json` — dùng làm golden và làm mẫu cài sẵn cho app.

## Dependencies

- KMP: `kotlinx-serialization-json` (parse). Dart: `dart:convert`.
- DESIGN-001, DESIGN-010.

## Risks / open questions

- Tiền tệ: `money` giả định minor = 1/100 (khớp SkytabOffline/SkyPos). Nếu có tiền tệ khác → cấu hình `MoneyFormat(minorUnits, separator, suffix)`.
- Nơi sửa mẫu (Master vs web) chưa chốt — không ảnh hưởng engine, chỉ ảnh hưởng app.
