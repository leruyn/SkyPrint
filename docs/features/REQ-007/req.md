# REQ-007 — Transport BLE (Android + iOS) (REQ)

## Actor(s)

- **Thu ngân**: in tới máy in BLE (phổ biến ở máy mini/cầm tay).
- **Thư viện**: ghi byte qua GATT characteristic.

## Goal

In tới máy in BLE trên cả Android và iOS với cùng API, không gắn cứng UUID của một hãng.

## Preconditions

- Quyền Bluetooth đã cấp (Android 12+: `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`; iOS: `NSBluetoothAlwaysUsageDescription`).

## Main flow

1. System: kết nối peripheral theo id (Android: MAC, iOS: UUID của CoreBluetooth).
2. System: tìm characteristic ghi — ưu tiên UUID cấu hình; nếu không cấu hình, dò danh sách UUID phổ biến (`FF00/FF02`, `18F0/2AF1`, `E7810A71-…`, `49535343-…`) rồi tới characteristic đầu tiên có `write`/`writeWithoutResponse`.
3. System: xin MTU lớn nhất (Android `requestMtu(512)`), chia byte theo `MTU - 3`, ghi tuần tự; `writeWithoutResponse` có pacing để không tràn buffer máy in.
4. System: ngắt kết nối (hoặc giữ theo keep-alive).

## Alternate / exception flows

- Không tìm thấy characteristic ghi: `UNSUPPORTED_DEVICE`, trả danh sách service để debug.
- Mất kết nối giữa chừng: `DISCONNECTED`.

## Postconditions

- Không để lại kết nối GATT treo (Android giới hạn số GATT client).

## Out of scope

- Đọc trạng thái giấy qua notify — v2.

## Acceptance criteria (feeds TEST-UAT-*)

- [ ] Cùng 1 máy in BLE in được từ Android và iPhone.
- [ ] Hoá đơn raster 30 KB in trọn vẹn, không mất đoạn (kiểm pacing).
- [ ] In 10 lần liên tiếp không lỗi `GATT 133`.
