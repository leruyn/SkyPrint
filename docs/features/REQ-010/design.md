# DESIGN-010 — Lệnh mở rộng + PrinterCapabilities (DESIGN)

> depends_on REQ-010, DESIGN-001 (mở rộng `Element`, `TextStyle`, encoder).

## Interfaces

```kotlin
// thêm vào DESIGN-001
data class TextStyle(..., val inverse: Boolean = false, val underline: Boolean = false, val width: Int = 1, val height: Int = 1)
sealed interface Element {
  ...
  data class Barcode(val data: String, val type: BarcodeType = BarcodeType.CODE128, val height: Int = 80, val hri: Boolean = true) : Element
  data class Drawer(val pin: Int = 0 /*0|1*/, val pulseMs: Int = 100) : Element
  data class Beep(val times: Int = 1, val durationMs: Int = 100) : Element
  data class Cut(val partial: Boolean = true) : Element   // thay data object Cut
}
enum class BarcodeType { CODE128, EAN13, EAN8, CODE39, UPCA }
data class PrinterCapabilities(val cutter: Boolean = true, val drawer: Boolean = false, val buzzer: Boolean = false,
                               val qr: Boolean = true, val barcode: Boolean = true, val inverse: Boolean = true,
                               val buzzerCommand: BuzzerCommand = BuzzerCommand.ESC_B) { companion object { val ALL: PrinterCapabilities } }
object CapabilityFilter { fun apply(doc: ReceiptDocument, caps: PrinterCapabilities): Pair<ReceiptDocument, List<String>> }
```

## Components

- `CapabilityFilter` (pure) — bỏ/thay element theo bảng: no cutter → `Feed(4)`; no qr → `Image(QrRasterizer)` nếu bật; no buzzer/drawer → bỏ.
- `QrRasterizer` (pure) — sinh QR matrix → `MonoBitmap` (KMP: port encoder QR nhỏ hoặc `qrose`/tự viết; Dart: `qr` package) — **mở**: chọn lib ở bước code.
- Encoder bổ sung lệnh: `ESC p m t1 t2` (t = pulseMs/2), `ESC B n t` hoặc `ESC ( A` (Epson-compatible buzzer), `GS B n`, `GS k m n d1..dn` (CODE128 cần tiền tố `{B`), `GS H n` (HRI), `GS h n`.

## Dependencies

- DESIGN-001. QR lib (mở).

## Risks / open questions

- Lệnh còi không chuẩn hoá giữa hãng (Xprinter `ESC B`, Epson `ESC ( A`) → cấu hình `buzzerCommand` theo máy, test thực tế ở TEST-SIT.
