# REQ-001 — Dựng hoá đơn ESC/POS (document model + encoder) (REQ)

## Actor(s)

- **App developer** (SkytabOffline KMP, SkyPos-Flutter): mô tả nội dung hoá đơn bằng API của thư viện.
- **Thư viện skyprint**: chuyển mô tả đó thành byte ESC/POS.

## Goal

Developer mô tả hoá đơn một lần bằng một document model khai báo (text, hàng cột, kẻ ngang, feed, cắt giấy) và nhận về byte ESC/POS đúng cho khổ giấy đã chọn, không phải tự ghép lệnh ESC/POS.

## Preconditions

- Đã biết khổ giấy máy in: 58mm (32 ký tự Font A, 384 dot) hoặc 80mm (48 ký tự, 576 dot).

## Main flow

1. Developer: tạo `ReceiptDocument` với `PaperWidth` và danh sách phần tử (`Text`, `Row`, `Divider`, `Feed`, `Cut`, `Image`, `QrCode`).
   System: kiểm tra hợp lệ (tổng weight cột > 0, feed 0..255).
2. Developer: gọi `EscPosEncoder.encode(document, TextMode.ASCII)`.
   System: phát `ESC @` reset → lệnh từng phần tử → trả `ByteArray`/`Uint8List`.
3. Developer: đưa byte cho transport bất kỳ (REQ-004..007) hoặc hàng đợi (REQ-008).

## Alternate / exception flows

- Text dài hơn chiều rộng dòng: tự xuống dòng theo từ; với `Row`, cắt label trước, không bao giờ cắt value (số tiền).
- Ký tự ngoài ASCII ở `TextMode.ASCII`: map bỏ dấu tiếng Việt; ký tự không map được thay bằng `?` (không bao giờ lọt UTF-8 multi-byte xuống máy in).
- Document không hợp lệ: trả lỗi `INVALID_DOCUMENT`, không phát byte nào.

## Postconditions

- Byte output chỉ phụ thuộc vào (document, mode, paper) — deterministic, test được bằng so sánh byte.

## Out of scope

- Định dạng tiền tệ/số (app tự format rồi truyền chuỗi vào).
- Barcode 1D, ngăn kéo tiền (cash drawer) — v2.
- Render tiếng Việt có dấu — thuộc REQ-002.

## Acceptance criteria (feeds TEST-UAT-*)

- [ ] Hoá đơn mẫu (tiêu đề to/đậm giữa, 5 món, tổng) in đúng bố cục trên máy 58mm và 80mm.
- [ ] Số tiền luôn căn phải, không bị cắt khi tên món dài.
- [ ] Giấy được cắt sau khi in (máy có dao cắt).
- [ ] KMP và Flutter cho ra byte giống hệt nhau với cùng document mẫu (golden file dùng chung).
