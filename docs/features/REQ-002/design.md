# DESIGN-002 — Raster text tiếng Việt + fallback ASCII (DESIGN)

> depends_on REQ-002, DESIGN-001.

## Interfaces

```kotlin
// core (pure)
class MonoBitmap(val width: Int, val height: Int, val bits: ByteArray /* row-major, MSB-first, width/8 bytes/row */)
object RasterPacker { fun toGsV0(bitmap: MonoBitmap, bandHeight: Int = 255): ByteArray }
object Dither { fun threshold(gray: ByteArray, width: Int, height: Int, level: Int = 128): MonoBitmap }

// platform
interface TextRasterizer {
  /** Render 1 khối (Text hoặc Row đã layout) rộng đúng [widthDots]. */
  fun render(block: RasterBlock, widthDots: Int): MonoBitmap
}
data class RasterBlock(val lines: List<RasterLine>)          // mỗi line: các span (text, align, bold, size, x-range)
```

Dart: `abstract class TextRasterizer { Future<MonoBitmap> render(RasterBlock b, int widthDots); }` (dart:ui bất đồng bộ).

## Components

- `RasterPacker` (core, pure) — đóng gói `GS v 0 m xL xH yL yH d...`, chia band ≤ 255 dot rows để máy in buffer nhỏ không tràn.
- `AndroidTextRasterizer` — `Bitmap.ARGB_8888` + `Canvas` + `TextPaint`/`StaticLayout`, font TTF bundle.
- `IosTextRasterizer` — `CoreText` (`CTLineDraw`) vào `CGBitmapContext` grayscale.
- `FlutterTextRasterizer` — `ParagraphBuilder` → `PictureRecorder` → `Picture.toImage` → `toByteData(rawRgba)`.
- Encoder mode RASTER: gom các Text/Row/Divider liên tiếp thành 1 `RasterBlock` (ít lệnh, in nhanh hơn từng dòng).

## Data model

- Font bundle: Be Vietnam Pro Regular/Bold (OFL 1.1) — cùng file TTF cho 3 renderer để golden image khớp.
- Golden: `spec/golden/<case>.raster.png` (1-bit) — so khớp ≤ 2% pixel.

## Dependencies

- DESIGN-001 (document, layout).

## Risks / open questions

- Anti-aliasing khác nhau giữa Skia (Android/Flutter) và CoreText → dung sai 2% pixel.
- Tốc độ: 80mm × 20 món ≈ 576×1200 dot ≈ 86 KB; BLE chậm → cân nhắc nén (`GS ( L` không phổ biến) — đo ở TEST-SIT.
