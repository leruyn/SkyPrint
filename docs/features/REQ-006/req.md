# REQ-006 — Transport Bluetooth Classic SPP (Android) (REQ)

## Actor(s)

- **Thu ngân**: in tới máy in Bluetooth cầm tay/để bàn đã ghép nối.
- **Thư viện**: gửi byte qua RFCOMM.

## Goal

In tới máy in Bluetooth Classic (SPP) đã ghép nối trong Cài đặt Android.

## Preconditions

- Máy in đã được ghép nối (bonded) qua Cài đặt hệ thống.
- Android 12+: quyền runtime `BLUETOOTH_CONNECT` đã cấp.

## Main flow

1. System: tìm device bonded theo MAC → `cancelDiscovery()` → `createRfcommSocketToServiceRecord(SPP UUID)` → `connect()`.
2. System: ghi byte theo chunk, flush.
3. System: đóng socket (hoặc giữ theo keep-alive).

## Alternate / exception flows

- `connect()` thất bại với secure socket: thử lại 1 lần bằng insecure RFCOMM (một số máy giá rẻ yêu cầu).
- Thiếu quyền: `PERMISSION_REQUIRED(BLUETOOTH_CONNECT)`.
- Bluetooth tắt: `ADAPTER_OFF`.

## Postconditions

- Socket đóng ở mọi nhánh lỗi.

## Out of scope

- iOS (Bluetooth Classic cần MFi/External Accessory — không hỗ trợ).
- Ghép nối trong app.

## Acceptance criteria (feeds TEST-UAT-*)

- [ ] In được tới 1 máy Bluetooth Classic phổ thông (Xprinter/Goojprt) đã ghép nối.
- [ ] Tắt máy in → lỗi rõ trong ≤ 10 giây.
- [ ] Hoá đơn raster in trọn vẹn, không mất dòng.
