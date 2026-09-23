# REQ-009 — Mẫu in cấu hình được (template engine có placeholder) (REQ)

> Quyết định 2026-09-23: skyprint render mẫu cho **cả online và offline** (không in theo mẫu RK7 server dựng nữa), để hoá đơn luôn giống nhau. Nghiên cứu: docs/research/pos-printing-business.md.

## Actor(s)

- **Chủ quán / quản lý**: chỉnh mẫu (bật/tắt khối, sửa chữ tĩnh) qua màn cấu hình của app.
- **App POS** (SkytabOffline, SkyPos-Flutter): cung cấp dữ liệu chứng từ + chọn mẫu theo định tuyến.
- **Thư viện skyprint**: ghép mẫu + dữ liệu thành `ReceiptDocument` (REQ-001).

## Goal

Một mẫu in (JSON có chỗ trống `{{...}}`) + dữ liệu chứng từ → hoá đơn/phiếu in đúng, mà không phải sửa code khi đổi bố cục hay nội dung.

## Preconditions

- App có dữ liệu chứng từ dạng cây (map/list/giá trị) theo schema của loại chứng từ (RECEIPT, PRECHECK, KITCHEN, KITCHEN_VOID, SHIFT_X, SHIFT_Z…).
- Mẫu đã lưu (JSON, có `version`).

## Main flow

1. Quản lý: sửa mẫu, bấm Lưu.
   System: `TemplateEngine.validate(template, schema)` — cú pháp, biến có trong schema, formatter tồn tại; lỗi trả vị trí (đường dẫn element + tên biến).
2. App: khi cần in, gọi `TemplateEngine.render(template, data, options)`.
   System: thay placeholder, lặp `each`, lọc `if`/`enabled`, áp formatter → `ReceiptDocument`.
3. App: gửi `ReceiptDocument` cho `PrinterService` (REQ-008), in theo textMode của máy in (REQ-002).
4. Quản lý: bấm "In thử".
   System: render mẫu với dữ liệu mẫu (sample data kèm schema) → in.

## Alternate / exception flows

- Biến thiếu trong dữ liệu lúc render: in chuỗi rỗng (hoặc giá trị `| default:'…'`), ghi cảnh báo; **không** huỷ lệnh in.
- Mẫu hỏng (JSON sai, version không hỗ trợ): `INVALID_TEMPLATE`, app dùng mẫu mặc định đóng gói sẵn và báo cho quản lý.
- Element cần năng lực máy in không có (QR, dao cắt, két): bỏ qua theo capabilities (REQ-010), không lỗi.

## Postconditions

- Cùng (mẫu, dữ liệu, options) → cùng `ReceiptDocument` ở KMP và Dart (golden chung).

## Out of scope

- Trình thiết kế kéo-thả (v2) — dùng lại cùng định dạng JSON.
- Lưu trữ/đồng bộ mẫu giữa máy (app lo; nơi sửa mẫu — Master hay web back-office — **chưa chốt**).
- Tem nhãn TSPL (v2, REQ-011).
- Biểu thức tính toán tuỳ ý / chạy script trong mẫu (cố ý không hỗ trợ — an toàn, dễ kiểm).

## Acceptance criteria (feeds TEST-UAT-*)

- [ ] Đổi tên quán, địa chỉ, footer, ẩn logo trong màn cấu hình → lần in kế tiếp phản ánh ngay, không build lại app.
- [ ] Hoá đơn có giảm giá hiện dòng "Giảm giá"; không giảm giá → không có dòng đó.
- [ ] Món có modifier in từng modifier thụt lề dưới món.
- [ ] In lại hoá đơn hiện "BẢN IN LẠI lần N".
- [ ] Mẫu tham chiếu biến không tồn tại → bị chặn lúc Lưu, báo đúng tên biến.
- [ ] Bộ mẫu mặc định (hoá đơn, tạm tính + QR, phiếu bếp, phiếu huỷ món, báo cáo ca Z) × (58mm, 80mm) in đúng trên KMP và Flutter.
