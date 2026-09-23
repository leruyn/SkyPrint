# REQ-010 — Lệnh ESC/POS cho nghiệp vụ POS + năng lực máy in (REQ)

## Actor(s)

- **Thu ngân**: mở két khi thu tiền mặt; nhìn phiếu huỷ món nổi bật.
- **Bếp**: nghe còi khi có phiếu mới.
- **Thư viện**: phát đúng lệnh, bỏ lệnh máy in không hỗ trợ.

## Goal

Hỗ trợ các lệnh phần cứng mà nghiệp vụ POS cần (mở két, còi, in đảo màu, mã vạch, cắt một phần/toàn phần, chữ nhiều cỡ), và tự bỏ lệnh không phù hợp theo năng lực khai báo của từng máy in.

## Preconditions

- App khai `PrinterCapabilities` cho từng máy in (mặc định: bật hết trừ drawer/buzzer).

## Main flow

1. App: document có `Drawer`, `Beep`, `Barcode`, `Text(style.inverse = true)`, `Cut(partial)`.
   System: phát `ESC p m t1 t2`, `ESC B n t` (hoặc `ESC ( A` tuỳ cấu hình), `GS k`, `GS B 1`, `GS V 66 0` / `GS V 65 0`.
2. System: đối chiếu `capabilities` — lệnh không hỗ trợ bị bỏ, ghi cảnh báo; QR không hỗ trợ → tự raster QR thành ảnh (nếu `rasterQrFallback`).

## Alternate / exception flows

- Máy in không có dao cắt: thay `Cut` bằng feed thêm 4 dòng.
- Barcode dữ liệu sai chuẩn (vd EAN13 không đủ 12–13 số): `INVALID_DOCUMENT` với vị trí element.

## Postconditions

- Không phát byte lạ cho máy in không hỗ trợ (tránh in ra ký tự rác).

## Out of scope

- Đọc trạng thái két/giấy (DLE EOT) — v2.
- Tem nhãn TSPL — v2 (REQ-011).

## Acceptance criteria (feeds TEST-UAT-*)

- [ ] Thanh toán tiền mặt → két mở; thanh toán thẻ/QR → két không mở.
- [ ] Phiếu huỷ món in dòng "HUỶ" đảo màu (nền đen chữ trắng) cỡ to.
- [ ] Máy in bếp có còi kêu khi nhận phiếu mới.
- [ ] Máy in không dao cắt: không in ký tự rác, giấy được đẩy đủ để xé.
