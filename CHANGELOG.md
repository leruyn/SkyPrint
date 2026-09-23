# Changelog

Định dạng theo [Keep a Changelog](https://keepachangelog.com/), version theo
[SemVer](https://semver.org/). Dự án khác dùng skyprint đọc file này trước
khi nâng version — xem chính sách tương thích ngược ở
[docs/architecture.md](docs/architecture.md#phiên-bản--publish).

## [Unreleased]

### Added

- **REQ-001** — Document model (`ReceiptDocument`, `Element`, `TextStyle`,
  `Column`, `MonoBitmap`) và `EscPosEncoder` chế độ `TextMode.ASCII` (KMP,
  `skyprint-core`): text, row (căn cột theo weight, số tiền không bao giờ bị
  cắt), divider, feed, cut, QR code (`GS ( k` chuẩn Epson model 2).
  [CODE-001](docs/wbs.md)
- **REQ-010** — Lệnh ESC/POS nghiệp vụ POS + `PrinterCapabilities` (KMP,
  `skyprint-core`): `Element.Barcode` (CODE128/EAN13/EAN8/CODE39/UPCA,
  validate độ dài ngay lúc dựng), `Element.Drawer` (mở két `ESC p`),
  `Element.Beep` (còi `ESC B` hoặc `ESC ( A` tuỳ máy), `TextStyle.inverse`
  (đảo màu `GS B`, dùng cho phiếu huỷ món) và `.underline` (`ESC -`).
  `CapabilityFilter.apply(document, capabilities)` lọc/thay phần tử máy in
  không hỗ trợ trước khi encode (không phát byte lạ), kèm cảnh báo.
  [CODE-002](docs/wbs.md)

- **REQ-009** — Template engine (module mới `skyprint-template`, KMP):
  `TemplateParser.parse()` đọc JSON mẫu in; `TemplateEngine.validate()` kiểm
  mọi biến `{{...}}`/`if`/`each` có thuộc schema chứng từ không (chặn lúc
  Lưu); `TemplateEngine.render()` ghép mẫu + dữ liệu thành `ReceiptDocument`
  của `skyprint-core` -- hỗ trợ nội suy `{{path | formatter:'arg'}}`
  (`money`, `number`, `upper`, `lower`, `default`, `truncate`, `pad`,
  `datetime`), vòng lặp `each`/`as` (kể cả lồng nhau), điều kiện `if`
  (`&&`/`||`/so sánh/phủ định), khối `enabled` tĩnh. Biến thiếu -> chuỗi
  rỗng + không huỷ lệnh in; phần tử có dữ liệu không hợp lệ sau nội suy (vd
  mã vạch sai độ dài) -> bỏ qua đúng phần tử đó kèm cảnh báo, không crash
  cả bản in. [CODE-003](docs/wbs.md)

- **REQ-008** — `PrinterConnection`/`PrinterTransport` (contract cho
  REQ-004..007 nối vào), `PrintResult`/`RetryPolicy`, `PrintQueue` (KMP,
  `skyprint-core`): mỗi máy in một hàng đợi riêng (khoá theo `printer.id`,
  không bao giờ 2 job ghi xen byte vào cùng 1 máy), retry có giới hạn chỉ
  với lỗi tạm thời (`PrinterErrorCode.retryable`), timeout toàn job, đóng
  kết nối kể cả khi job bị huỷ (`NonCancellable`). Thêm `PrinterInfo`/
  `TransportKind` (mượn trước từ DESIGN-003 -- discovery thật chưa cài).
  [CODE-004](docs/wbs.md)

- **REQ-002 (một phần)** — `RasterPacker` (đóng gói `MonoBitmap` thành lệnh
  `GS v 0`, chia dải theo `bandHeight` để không tràn buffer máy in),
  `Dither.threshold` (ảnh xám -> 1-bit theo ngưỡng), contract `TextRasterizer`/
  `RasterBlock`/`RasterLine`/`RasterSpan` (KMP, `skyprint-core`).
  `EscPosEncoder` giờ nhận `rasterizer: TextRasterizer?` -- `TextMode.RASTER`
  gom `Text`/`Row`/`Divider` liên tiếp thành 1 khối, gọi rasterizer 1 lần
  cho cả khối (ít lệnh `GS v 0` hơn); `Element.Image` dùng `RasterPacker`
  trực tiếp, hoạt động ở CẢ hai `TextMode`.
  **Chưa cài** (cần toolchain Android/iOS thật, không giả lập bằng JVM
  thuần được): `AndroidTextRasterizer` (Canvas/StaticLayout), iOS
  (CoreText), Flutter (dart:ui) -- việc riêng, theo dõi tiếp.
  [CODE-005](docs/wbs.md)

- **REQ-002 (thêm)** — `AndroidTextRasterizer` (KMP `androidMain`, dùng
  `Canvas`/`TextPaint`/`Bitmap` thật): vẽ mọi dòng bằng 1 font đơn cách
  (monospace) để giữ thẳng cột của `Row`/`Divider` (đã canh sẵn bằng
  khoảng trắng ở `LayoutEngine`), hỗ trợ bold/underline/inverse/width/
  height scale theo `TextStyle`. Thêm `androidTarget()` +
  `com.android.library` (ghim AGP 8.7.3 -- AGP 9+ cấm tổ hợp này với KMP,
  xem `kmp/build.gradle.kts`) + Robolectric 4.14.1 cho `androidUnitTest`.
  **Giới hạn đã xác nhận, không phải bỏ sót:** trong tổ hợp cụ thể của
  repo này, pipeline vẽ Skia native của Robolectric không kích hoạt được
  (`measureText`/`drawText`/`fontMetrics` đều trả stub, đã xác minh bằng
  test tay, đã thử `sdk=[34]` và `manifest=Config.NONE` không đổi kết
  quả) -- test hiện tại chỉ kiểm không crash + kích thước ảnh hợp lệ,
  CHƯA kiểm được nội dung glyph/canh lề/độ cao chữ thật. Cần xác minh
  trên thiết bị/emulator Android thật hoặc đổi cấu hình Robolectric
  trước khi tin tưởng hoàn toàn bản raster Android. [CODE-006](docs/wbs.md)

### Changed

- **Vỡ tương thích (chưa publish, chấp nhận được):** `TextStyle.size: Int`
  (1..8, gộp cả 2 chiều) đổi thành `width: Int` + `height: Int` độc lập —
  khớp đúng 2 nibble thật của lệnh `GS ! n`, cần thiết để REQ-010 có thể in
  chữ phóng ngang/dọc riêng (vd chữ cao gấp đôi nhưng không rộng gấp đôi).
  `Element.Cut` (đối tượng) đổi thành `Element.Cut(partial: Boolean = true)`
  (lớp dữ liệu) để hỗ trợ cắt toàn phần bên cạnh cắt một phần.
