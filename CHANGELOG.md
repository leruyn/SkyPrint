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

### Changed

- **Vỡ tương thích (chưa publish, chấp nhận được):** `TextStyle.size: Int`
  (1..8, gộp cả 2 chiều) đổi thành `width: Int` + `height: Int` độc lập —
  khớp đúng 2 nibble thật của lệnh `GS ! n`, cần thiết để REQ-010 có thể in
  chữ phóng ngang/dọc riêng (vd chữ cao gấp đôi nhưng không rộng gấp đôi).
  `Element.Cut` (đối tượng) đổi thành `Element.Cut(partial: Boolean = true)`
  (lớp dữ liệu) để hỗ trợ cắt toàn phần bên cạnh cắt một phần.
