# Nghiệp vụ in cho máy POS & cách cấu hình mẫu in

> Tài liệu nghiên cứu (chưa phải REQ). Đầu vào để chốt REQ-009+ trong traceability.
> Nguồn: code legacy `skytab-offline`, tài liệu KiotViet / CukCuk / Sapo / r_keeper 7, Nghị định 70/2025/NĐ-CP.

## 1. Hiện trạng Skytab

| Chỗ | Cách làm | Hạn chế |
|---|---|---|
| `skytab-offline` (legacy) | RK7 server dựng sẵn nội dung (`PrintingInfo.printingData` = text thô), app chỉ gửi đi; bảng `op_prints` lưu hàng đợi (QUEUED/DONE/FAIL), loại `Receipt / Service / Report` | Offline không có RK7 → không có mẫu. `SkyPrintTxt` dựng hoá đơn **hard-code** ("Restaurant name", "Address line 1"…), báo cáo ca `generateShftData` trả rỗng |
| `SkytabOffline` (KMP) | `ReceiptFormatter` hard-code 1 mẫu hoá đơn | Không có phiếu bếp, tạm tính, báo cáo ca, không cấu hình được |
| `SkyPos-Flutter` | `ReceiptBuilder` hard-code 1 mẫu | Như trên, thêm lỗi số tiền minor |

→ Chế độ offline **bắt buộc app tự dựng mẫu**, nên cần cơ chế mẫu cấu hình được thay cho hard-code.

## 2. Danh mục nghiệp vụ in (POS F&B)

Ưu tiên: **P0** = phải có để vận hành nhà hàng, **P1** = nên có, **P2** = sau.

| # | Chứng từ | Khi nào in | Máy in | Điểm đặc thù | Ưu tiên |
|---|---|---|---|---|---|
| 1 | **Hoá đơn thanh toán** | Sau thanh toán (tự động/bấm) | Quầy | Số bản (copies); in lại phải ghi **"BẢN IN LẠI"** + lần in; thanh toán nhiều phương thức; tiền thối | P0 |
| 2 | **Phiếu tạm tính** (pre-check) | Khách xin xem bill trước khi trả | Quầy | Ghi rõ **"TẠM TÍNH"**, đếm số lần in; **QR thanh toán động** (VietQR/Momo/VNPay) | P0 |
| 3 | **Phiếu chế biến bếp/bar** | Khi gửi món (send order) | Theo khu chế biến | **Định tuyến món theo nhóm/khu** (bếp nóng, bếp lạnh, bar); 1 phiếu/order hoặc 1 phiếu/món; chữ to; ghi chú, modifier, course, bàn, người order, giờ; **còi báo** máy in bếp | P0 |
| 4 | **Phiếu huỷ / đổi món** | Huỷ món đã gửi bếp, sửa số lượng | Khu chế biến của món đó | Nổi bật **"HUỶ"** (in đảo màu/chữ to), lý do huỷ, người duyệt | P0 |
| 5 | **Báo cáo ca** X (giữa ca) / Z (đóng ca) | Đóng ca, cuối ngày | Quầy | Doanh thu theo phương thức thanh toán, giảm giá, huỷ món, tiền đầu ca/cuối ca | P0 |
| 6 | Phiếu chuyển/gộp bàn | Chuyển bàn khi đã gửi bếp | Bếp | Bàn cũ → bàn mới | P1 |
| 7 | **Hoá đơn điện tử từ máy tính tiền** | Thanh toán (nếu nhà hàng ≥ 1 tỷ/năm) | Quầy | Theo NĐ 70/2025: tên/MST/địa chỉ người bán, hàng hoá, **mã của cơ quan thuế hoặc dữ liệu/QR để tra cứu** | P1 (bắt buộc theo luật với nhiều khách) |
| 8 | Mở két tiền | Thanh toán tiền mặt | Quầy (két nối máy in) | Lệnh `ESC p`, không in giấy | P1 |
| 9 | **Tem nhãn đồ uống** | Gửi món / bếp trả món | Máy in tem | Mỗi ly 1 tem: tên, size, đường/đá, topping, số order. **Máy in tem dùng TSPL, không phải ESC/POS** | P1 (trà sữa/cafe) |
| 10 | Số thứ tự / phiếu gọi món | Order tại quầy | Quầy | Số to, mã vạch/QR | P2 |
| 11 | Phiếu thu/chi tiền mặt | Thu chi ngoài bán hàng | Quầy | | P2 |
| 12 | Phiếu giao hàng (Grab/Shopee) | Đơn delivery | Quầy | Mã đơn đối tác, tài xế | P2 |

## 3. Cấu hình in gồm 3 lớp

Các phần mềm tham khảo (r_keeper 7, CukCuk, KiotViet) đều tách cấu hình thành 3 lớp giống nhau:

```
Máy in (Printer)  ──  Mục đích in / định tuyến (Route)  ──  Mẫu in (Template)
```

### 3.1 Máy in — thiết bị vật lý

```json
{ "id": "kitchen-hot", "name": "Bếp nóng", "connection": { "kind": "LAN", "host": "192.168.1.50", "port": 9100 },
  "paper": "MM80", "textMode": "RASTER",
  "capabilities": { "cutter": true, "drawer": false, "buzzer": true, "qr": true, "language": "ESCPOS" },
  "fallbackPrinterId": "kitchen-cold" }
```

### 3.2 Định tuyến — chứng từ nào in ở máy nào, bao nhiêu bản, tự động hay không

r_keeper 7 định tuyến phiếu bếp theo **classification** (phân loại món → nơi chế biến); CukCuk theo **khu chế biến** gán cho từng món. Mô hình đề xuất:

```json
{ "routes": [
  { "document": "RECEIPT",      "printers": ["cashier"],  "template": "receipt-default", "copies": 1, "auto": true },
  { "document": "PRECHECK",     "printers": ["cashier"],  "template": "precheck-qr",     "copies": 1, "auto": false },
  { "document": "KITCHEN",      "byStation": { "HOT": "kitchen-hot", "COLD": "kitchen-cold", "BAR": "bar" },
                                "template": "kitchen-ticket", "split": "PER_ORDER" },
  { "document": "KITCHEN_VOID", "byStation": "same-as-KITCHEN", "template": "kitchen-void" },
  { "document": "DRINK_LABEL",  "printers": ["label-1"],  "template": "label-40x30", "split": "PER_UNIT" },
  { "document": "SHIFT_Z",      "printers": ["cashier"],  "template": "shift-z" }
]}
```

`split`: `PER_ORDER` (1 phiếu cho cả lượt gửi) · `PER_ITEM` (1 phiếu/món) · `PER_UNIT` (1 phiếu/đơn vị, vd 3 ly = 3 tem).

### 3.3 Mẫu in — "mẫu đục lỗ" (mẫu cố định, chừa chỗ trống để điền dữ liệu)

Có 3 cách làm phổ biến:

| Cách | Ai dùng | Ưu | Nhược |
|---|---|---|---|
| A. **Bật/tắt khối có sẵn** (checkbox: logo, địa chỉ, QR, ghi chú…) | Sapo, CukCuk (mức cơ bản) | Dễ cho chủ quán | Không đổi được bố cục |
| B. **Mẫu có placeholder** (chèn biến bằng `/token`, `{{...}}`) | KiotViet, DantSu markup (`[C]<b>…</b>`) mà legacy đang dùng | Linh hoạt, lưu được dạng text/JSON | Cần validate biến |
| C. Trình thiết kế kéo-thả | CukCuk Report Manager, FastReport của RK7 | Mạnh nhất | Tốn công làm editor |

**Đề xuất: B làm lõi, A làm giao diện.** Mẫu lưu dạng **JSON khai báo** (có version, đồng bộ từ server về máy). Engine render mẫu nằm trong `skyprint` (có cả bản KMP và Dart, kiểm bằng golden test chung). Màn cấu hình cho chủ quán chỉ bật/tắt khối và sửa text tĩnh, rồi sinh ra chính JSON đó. Làm C sau (v2), dùng lại cùng định dạng JSON.

Cú pháp đề xuất (tối giản, không cho chạy code tuỳ ý):

- Biến: `{{store.name}}`, `{{order.table}}`
- Bộ định dạng: `{{total | money}}`, `{{closedAt | datetime:'HH:mm dd/MM'}}`, `{{name | upper}}`
- Vòng lặp: `"each": "items"` · điều kiện: `"if": "discount > 0"` · khối bật/tắt: `"enabled": false`

**Ví dụ — mẫu hoá đơn 80mm** (`receipt-default`):

```json
{
  "id": "receipt-default", "version": 3, "document": "RECEIPT", "paper": "MM80",
  "elements": [
    { "type": "image",   "source": "{{store.logo}}", "enabled": true },
    { "type": "text",    "value": "{{store.name}}", "style": { "align": "CENTER", "bold": true, "size": 2 } },
    { "type": "text",    "value": "{{store.address}} · ĐT {{store.phone}}", "style": { "align": "CENTER" } },
    { "type": "text",    "value": "HOÁ ĐƠN THANH TOÁN", "style": { "align": "CENTER", "bold": true } },
    { "type": "text",    "value": "*** BẢN IN LẠI lần {{print.count}} ***", "if": "print.isReprint", "style": { "align": "CENTER" } },
    { "type": "row",     "columns": [ { "text": "Bàn: {{order.table}}" }, { "text": "Số: {{order.checkNo}}", "align": "RIGHT" } ] },
    { "type": "row",     "columns": [ { "text": "Thu ngân: {{order.cashier}}" }, { "text": "{{order.closedAt | datetime:'HH:mm dd/MM/yyyy'}}", "align": "RIGHT" } ] },
    { "type": "divider" },
    { "type": "row", "header": true, "columns": [ { "text": "Món", "weight": 6 }, { "text": "SL", "weight": 1, "align": "RIGHT" }, { "text": "T.Tiền", "weight": 3, "align": "RIGHT" } ] },
    { "type": "group", "each": "items", "as": "it", "elements": [
        { "type": "row", "columns": [ { "text": "{{it.name}}", "weight": 6 }, { "text": "{{it.qty}}", "weight": 1, "align": "RIGHT" }, { "text": "{{it.amount | money}}", "weight": 3, "align": "RIGHT" } ] },
        { "type": "text", "value": "  + {{m.name}}", "each": "it.modifiers", "as": "m" }
    ]},
    { "type": "divider" },
    { "type": "row", "columns": [ { "text": "Tạm tính" }, { "text": "{{order.subtotal | money}}", "align": "RIGHT" } ] },
    { "type": "row", "if": "order.discount > 0", "columns": [ { "text": "Giảm giá" }, { "text": "-{{order.discount | money}}", "align": "RIGHT" } ] },
    { "type": "row", "style": { "bold": true, "size": 2 }, "columns": [ { "text": "TỔNG" }, { "text": "{{order.total | money}}", "align": "RIGHT" } ] },
    { "type": "group", "each": "payments", "as": "p", "elements": [
        { "type": "row", "columns": [ { "text": "{{p.name}}" }, { "text": "{{p.amount | money}}", "align": "RIGHT" } ] } ] },
    { "type": "qr",      "value": "{{einvoice.lookupUrl}}", "if": "einvoice", "caption": "Tra cứu HĐĐT · Mã CQT {{einvoice.taxCode}}" },
    { "type": "text",    "value": "{{store.footer}}", "style": { "align": "CENTER" } },
    { "type": "feed", "lines": 3 }, { "type": "cut" }, { "type": "drawer", "if": "payments.hasCash" }
  ]
}
```

Mỗi loại chứng từ có **schema dữ liệu cố định** (vd `RECEIPT` có `store, order, items[], payments[], print, einvoice?`). Editor chỉ gợi ý các biến có trong schema, và engine báo lỗi ngay khi gặp biến lạ lúc lưu mẫu (không đợi tới lúc in mới lỗi).

"Đục lỗ" theo nghĩa vật lý (phiếu có cuống để xé) cũng làm được: dùng lệnh cắt một phần `GS V 66` hoặc dòng `- - ✂ - -`, rồi in tiếp phần cuống, ví dụ số thứ tự cho khách giữ.

## 4. Tác động tới thư viện `skyprint`

| Hạng mục | Thuộc về | Đề xuất REQ |
|---|---|---|
| Template engine: JSON + placeholder + each/if/format → `ReceiptDocument` (KMP + Dart, golden chung) | skyprint | **REQ-009** |
| Mở rộng lệnh ESC/POS: mở két `ESC p`, còi `ESC B`, in đảo màu `GS B`, mã vạch `GS k`, cắt một phần, chữ size 1–8 | skyprint (mở rộng REQ-001) | **REQ-010** |
| In tem nhãn **TSPL** (khổ tem, gap, tem 40×30/50×30) — ngôn ngữ lệnh khác ESC/POS nhưng transport dùng lại được | skyprint | **REQ-011** |
| Capabilities của máy in (cutter/drawer/buzzer/QR) → engine tự bỏ lệnh máy in không hỗ trợ | skyprint | gộp REQ-010 |
| Registry máy in, định tuyến theo khu chế biến, số bản, tự động in, máy in dự phòng, hàng đợi bền vững + log in lại | **App** (SkytabOffline / SkyPos) — domain POS, không nằm trong thư viện | ghi vào traceability của app |
| Schema dữ liệu từng chứng từ + bộ mẫu mặc định (hoá đơn, tạm tính, bếp, huỷ món, ca Z, tem) | App (dùng engine REQ-009) | app |
| HĐĐT máy tính tiền: lấy mã CQT/URL tra cứu từ nhà cung cấp HĐĐT | App + backend | app |

## 5. Quyết định & câu hỏi còn mở

Đã chốt (2026-09-23):
- ✅ **skyprint render mẫu cho cả online và offline** → REQ-009.
- ✅ **Tem nhãn TSPL để v2** (REQ-011, chưa tạo node).
- ✅ Lệnh mở rộng + capabilities → REQ-010.

Câu hỏi gốc:

1. **Nguồn mẫu khi online:** vẫn in theo mẫu RK7 server dựng (như legacy), hay dùng mẫu skyprint cho cả online và offline để hoá đơn luôn giống nhau? *Đề xuất: dùng skyprint cho cả hai.*
2. **Nơi quản lý mẫu:** sửa trên máy POS (Master) rồi đồng bộ sang Terminal, hay sửa trên web back-office?
3. **Tem nhãn (TSPL)** có nằm trong v1 không? (Chỉ cần nếu có khách trà sữa/cafe.)
4. **HĐĐT máy tính tiền** dùng nhà cung cấp nào (MISA meInvoice, Viettel, VNPT, BKAV…)? Việc này quyết định dữ liệu `einvoice` trong mẫu.

## Nguồn

- r_keeper 7 — Printing issues (classification → service scheme): https://docs.rkeeper.com/display/Fortranslate2/R-Keeper+7+Printing+issues
- CukCuk — mẫu phiếu chế biến: https://helpv2.cukcuk.vn/vi/kb/2080000_tuy_chinh_mau_order · máy in bếp/bar: https://helpv2.cukcuk.vn/vi/kb/2080000_thiet_lap_may_in_bep_bar · in tem sau khi trả món: https://helpv2.cukcuk.vn/vi/kb/bep-bar-co-the-in-tem-nhan-sau-khi-tra-mon
- KiotViet — quản lý mẫu in: https://www.kiotviet.vn/huong-dan-su-dung-kiotviet/thiet-lap/quan-ly-mau-in/ · thiết lập in F&B: https://www.kiotviet.vn/huong-dan-su-dung-kiotviet/thu-ngan-bar-cafe-nha-hang/thiet-lap-in/
- Sapo — cấu hình mẫu in POS: https://help.sapo.vn/cau-hinh-mau-in-hoa-don-tai-quay-pos
- NĐ 70/2025 — HĐĐT từ máy tính tiền: https://xaydungchinhsach.chinhphu.vn/mot-so-noi-dung-moi-cua-nghi-dinh-so-70-2025-nd-cp-ve-hoa-don-chung-tu-119250403074719995.htm · https://fpt-is.com/goc-nhin-so/quy-dinh-hoa-don-dien-tu-khoi-tao-tu-may-tinh-tien/
