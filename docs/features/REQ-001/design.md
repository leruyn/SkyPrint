# DESIGN-001 — Document model + ESC/POS encoder (DESIGN)

> depends_on REQ-001. Tổng quan: docs/architecture.md.

## Interfaces

KMP (`skyprint-core`, package `com.dcorp.skyprint.core`):

```kotlin
enum class PaperWidth(val columns: Int, val dots: Int) { MM58(32, 384), MM80(48, 576) }
enum class Align { LEFT, CENTER, RIGHT }
data class TextStyle(val align: Align = Align.LEFT, val bold: Boolean = false, val size: Int = 1 /*1..8*/)
sealed interface Element {
  data class Text(val text: String, val style: TextStyle = TextStyle()) : Element
  data class Row(val columns: List<Column>) : Element          // Column(text, weight, align, bold)
  data class Divider(val char: Char = '-') : Element
  data class Feed(val lines: Int) : Element
  data class QrCode(val data: String, val moduleSize: Int = 6, val align: Align = Align.CENTER) : Element
  data class Image(val bitmap: MonoBitmap, val align: Align = Align.CENTER) : Element
  data object Cut : Element
}
data class ReceiptDocument(val paper: PaperWidth, val elements: List<Element>)
enum class TextMode { ASCII, RASTER }
object EscPosEncoder { fun encode(doc: ReceiptDocument, mode: TextMode, rasterizer: TextRasterizer? = null): ByteArray }
```

Dart (`skyprint_core`): cùng tên, cùng field (`sealed class Element`, `Uint8List encode(...)`).

## Components

- `LayoutEngine` — word-wrap theo `columns / size`, chia cột theo weight, cắt label giữ value (thuần, test không cần thiết bị).
- `EscPosCommands` — hằng số lệnh: `ESC @`, `ESC a n`, `ESC E n`, `GS ! n`, `ESC d n`, `GS V 66 0`, QR `GS ( k`.
- `AsciiFolding` — bảng bỏ dấu tiếng Việt; ký tự ngoài bảng và > 0x7F → `?`.
- `EscPosEncoder` — duyệt elements → Commands; mode RASTER uỷ Text/Row/Divider cho REQ-002.

## Data model

- Golden: `spec/golden/<case>.json` (document) + `<case>.ascii.bin` (expected bytes). JSON schema chung cho 2 bản, loader có trong test mỗi bản.

## Dependencies

- Không có thư viện ngoài (KMP: chỉ stdlib; Dart: chỉ `dart:typed_data`, `dart:convert`).

## Risks / open questions

- Một số máy in không hỗ trợ QR `GS ( k` → v1 cho phép app tự raster QR thành `Image` nếu cần.
