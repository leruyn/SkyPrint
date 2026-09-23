# REQ-003 — Tìm máy in (discovery) đa transport (REQ)

## Actor(s)

- **Quản lý / thu ngân**: chọn máy in trong màn Cài đặt.
- **Thư viện**: liệt kê máy in khả dụng trên thiết bị.

## Goal

Thấy danh sách máy in thật (không lẫn thiết bị USB/Bluetooth khác) theo từng transport, rồi chọn một máy để lưu.

## Preconditions

- Transport tương ứng được hỗ trợ trên nền tảng (xem bảng trong design).

## Main flow

1. Actor: mở màn chọn máy in.
   System: gọi `PrinterDiscovery.discover(transports, timeout)` → phát `Flow`/`Stream` các `PrinterInfo(id, name, transport, address)` khi tìm thấy.
2. System: USB — lọc interface class 7 (Printer); Bluetooth Classic — thiết bị đã ghép nối; BLE — scan có service ghi được; LAN — dò subnet cổng 9100 (hoặc người dùng nhập IP).
3. Actor: chọn máy.
   System: trả `PrinterInfo` để app lưu (`id` ổn định qua lần mở app).

## Alternate / exception flows

- Thiếu quyền (Bluetooth scan/connect, Location cho BLE Android ≤ 11): trả lỗi `PERMISSION_REQUIRED` kèm tên quyền, không crash.
- Transport không hỗ trợ trên nền tảng (vd USB trên iOS): bỏ qua, không lỗi.
- LAN: người dùng nhập IP thủ công → kiểm tra kết nối TCP 9100 trong 3 giây.

## Postconditions

- Mỗi máy in xuất hiện đúng 1 lần; `id` dùng lại được cho transport ở REQ-004..007.

## Out of scope

- Lưu lựa chọn máy in (app tự lưu `PrinterInfo`).
- Ghép nối Bluetooth trong app (dùng Cài đặt hệ thống).

## Acceptance criteria (feeds TEST-UAT-*)

- [ ] ACE3 + ICOD USB: chỉ hiện 1 máy in, không hiện 3 thiết bị USB như bản cũ.
- [ ] Máy in LAN cùng subnet hiện trong ≤ 10 giây.
- [ ] Máy in BLE hiện trên cả Android và iPhone.
- [ ] Từ chối quyền Bluetooth → hiện thông báo rõ, không crash.
