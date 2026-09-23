# REQ-002 — In tiếng Việt: raster text + fallback ASCII (REQ)

## Actor(s)

- **Thu ngân / khách hàng**: đọc hoá đơn có dấu.
- **App developer**: chọn chế độ in text.

## Goal

Hoá đơn in tiếng Việt có dấu đúng trên mọi máy in nhiệt ESC/POS, kể cả máy giá rẻ không có codepage tiếng Việt.

## Preconditions

- Máy in hỗ trợ lệnh raster `GS v 0` (gần như mọi máy in nhiệt ESC/POS).
- Có font Unicode đủ glyph tiếng Việt đi kèm thư viện (bundle, license cho phép phân phối — vd. Noto Sans / Be Vietnam Pro, OFL).

## Main flow

1. Developer: gọi `encode(document, TextMode.RASTER)`.
   System: render mỗi dòng/khối text thành bitmap 1-bit rộng đúng số dot của khổ giấy (384/576), giữ align/bold/size.
2. System: dither/threshold → đóng gói `GS v 0` theo từng dải (band) ≤ 255 dòng dot.
3. System: phần tử không phải text (feed, cut, QR) vẫn phát lệnh ESC/POS gốc.

## Alternate / exception flows

- Developer chọn `TextMode.ASCII`: bỏ dấu (như REQ-001) — dùng cho máy in chậm hoặc in bếp cần nhanh.
- Render thất bại (thiếu font, lỗi native canvas): tự fallback ASCII và báo cảnh báo `RASTER_FALLBACK` trong kết quả, không hỏng lệnh in.

## Postconditions

- Người đọc thấy đủ dấu (ă â đ ê ô ơ ư + 5 thanh) trên giấy.

## Out of scope

- Codepage CP1258/TCVN3 (có thể bổ sung v2 như một `TextMode` nữa).
- Ngôn ngữ RTL / CJK — không cấm, nhưng không nằm trong acceptance.

## Acceptance criteria (feeds TEST-UAT-*)

- [ ] Câu mẫu "Phở bò tái — Cảm ơn quý khách! Đặc biệt ỹ ự ẵ" in rõ, đủ dấu trên ICOD 58mm và 1 máy 80mm.
- [ ] Hoá đơn 20 món ở chế độ raster in xong ≤ 5 giây qua USB và LAN.
- [ ] Chế độ ASCII in ra không dấu, không ký tự rác.
- [ ] Bitmap cùng document giữa KMP và Flutter khác nhau ≤ 2% pixel (golden image).
