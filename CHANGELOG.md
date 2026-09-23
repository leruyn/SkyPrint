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
  height scale theo `TextStyle`.
  **Giới hạn đã xác nhận, không phải bỏ sót:** đã thử CẢ 2 plugin Android
  cho module KMP -- `com.android.library` cổ (AGP 8.7.3) rồi
  `com.android.kotlin.multiplatform.library` chính thức (AGP 8.13.1,
  bản chốt) -- kết quả GIỐNG HỆT ở cả 2: pipeline vẽ Skia native của
  Robolectric 4.14.1 không kích hoạt trên máy này (`measureText`/
  `drawText`/`fontMetrics` đều trả stub cố định, xác minh bằng test tay;
  không phải thiếu binary -- file `.dylib` cho mac/aarch64 có trên
  classpath). Kết luận: vấn đề môi trường/Robolectric, KHÔNG PHẢI do chọn
  sai plugin -- đã loại trừ bằng thực nghiệm. Test hiện tại chỉ kiểm
  không crash + kích thước ảnh hợp lệ, CHƯA kiểm được nội dung glyph/canh
  lề/độ cao chữ thật -- cần xác minh trên thiết bị/emulator Android thật
  trước khi tin tưởng hoàn toàn bản raster Android. [CODE-006](docs/wbs.md)

- **REQ-004** — USB transport (KMP, `androidMain`): `UsbPrinterTransport`
  (dò máy in theo interface class 7, ghi qua bulk OUT endpoint),
  `UsbPermissionGate` (xin quyền USB, đọc lại `hasPermission()` sau
  broadcast thay vì tin `EXTRA_PERMISSION_GRANTED` -- tránh lớp lỗi
  `FLAG_IMMUTABLE` đã gặp ở bản legacy), `ChunkedWriter` (`commonMain`,
  thuần Kotlin -- chia gói ghi và phát hiện `transferred <= 0`, tránh
  vòng lặp vô hạn đã thấy ở code cũ). Định danh máy in USB dùng
  vendorId/productId (không dùng `deviceName`, vốn đổi mỗi lần cắm lại).
  Test qua Robolectric **thật, không stub** (khác gap của REQ-002 --
  `UsbManager`/`UsbDevice` là shadow dữ liệu thuần, không qua Skia native).
  **Đã xác minh trên thiết bị thật 2026-09-23** (ACE3 + ICOD_Thermal_Printer
  qua USB, app throwaway `:printtest` dựng riêng để verify rồi gỡ khỏi
  repo và máy): 4/4 lần in liên tiếp `open()`/`write()`/`close()` đều
  thành công, không crash, giấy in ra đúng nội dung -- đóng nốt phần
  "chưa test ghép nối đầy đủ" còn treo từ bản trước. [CODE-007](docs/wbs.md)

- **REQ-005** — `LanPrinterTransport` (KMP `commonMain`, dùng
  `ktor-network` 3.4.1): in qua LAN/Wi-Fi bằng raw TCP, mặc định cổng 9100
  (JetDirect). Chạy được cả Android và iOS mà không cần code riêng nền
  tảng (khác REQ-004's USB) -- chỉ chưa build cho iOS vì Gradle chưa thêm
  `iosArm64()` target. Test bằng server TCP thật chạy loopback ngay trong
  test (bind cổng ngẫu nhiên, accept, đọc byte) -- mạng/OS thật, không
  mock, dù không phải máy in thật. Bắt 1 bug thật lúc chạy: `parseAddress`
  trả nhầm cả chuỗi gốc (còn dính phần port hỏng) làm host khi port không
  parse được số -- sửa trước khi commit. [CODE-008](docs/wbs.md)

- **REQ-007** — `BlePrinterTransport` (KMP `commonMain`, dùng Kable
  0.45.0): in qua BLE, chạy được cả Android và iOS không cần code riêng
  nền tảng (giống REQ-005's LAN, khác REQ-004's USB). `CharacteristicResolver`
  chọn UUID service/characteristic để ghi theo thứ tự: cấu hình tường
  minh của app → danh sách UUID phổ biến của máy in nhiệt BLE giá rẻ
  (`BleDefaults.KNOWN_PAIRS`, theo đúng thứ tự khai) → characteristic ghi
  được đầu tiên tìm thấy (ưu tiên `WithResponse` có xác nhận hơn
  `WithoutResponse`). Ghi theo MTU thật (trừ 3 byte header ATT, mặc định
  an toàn 20 nếu truy vấn MTU thất bại), có nghỉ giữa các gói khi dùng
  `WithoutResponse` để không tràn buffer máy in giá rẻ.
  API Kable thật đã soi qua javap trước khi viết (không đoán theo tài
  liệu tóm tắt) -- biên dịch sạch cả JVM lẫn Android target.
  **Chưa test** `open()`/`write()` với thiết bị BLE thật -- chưa có máy
  in BLE sẵn để verify (khác REQ-004 lúc có ACE3 + ICOD sẵn), và Kable
  không cung cấp fake/mock cho BLE stack; `CharacteristicResolver` (phần
  logic dễ sai nhất) đã test đầy đủ bằng dữ liệu thuần.
  [CODE-009](docs/wbs.md)

### Changed

- **Vỡ tương thích (chưa publish, chấp nhận được):** `TextStyle.size: Int`
  (1..8, gộp cả 2 chiều) đổi thành `width: Int` + `height: Int` độc lập —
  khớp đúng 2 nibble thật của lệnh `GS ! n`, cần thiết để REQ-010 có thể in
  chữ phóng ngang/dọc riêng (vd chữ cao gấp đôi nhưng không rộng gấp đôi).
  `Element.Cut` (đối tượng) đổi thành `Element.Cut(partial: Boolean = true)`
  (lớp dữ liệu) để hỗ trợ cắt toàn phần bên cạnh cắt một phần.
