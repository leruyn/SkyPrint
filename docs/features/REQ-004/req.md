# REQ-004 — Transport USB (Android) (REQ)

## Actor(s)

- **Thu ngân**: bấm in; lần đầu đồng ý hộp thoại quyền USB.
- **Thư viện**: gửi byte qua USB host.

## Goal

In qua máy in cắm cáp USB (USB Printer-class) trên thiết bị Android ổn định từ Android 7 tới Android 16.

## Preconditions

- Thiết bị có USB host/OTG; máy in có interface class 7 và bulk OUT endpoint.

## Main flow

1. Actor: in lần đầu.
   System: `hasPermission` = false → gửi `requestPermission` (PendingIntent `FLAG_MUTABLE` + intent explicit `setPackage`, receiver `RECEIVER_NOT_EXPORTED`) → chờ kết quả, quyết định dựa trên `UsbManager.hasPermission(device)` sau broadcast (không tin extra).
2. System: tìm interface class 7 → bulk OUT endpoint → `openDevice` → `claimInterface(force=true)`.
3. System: ghi chia chunk (≤ 16 KB, mặc định 4 KB), timeout mỗi chunk; mọi `bulkTransfer ≤ 0` là lỗi.
4. System: release interface + close sau khi xong (hoặc giữ kết nối theo cấu hình keep-alive).

## Alternate / exception flows

- Từ chối quyền / hộp thoại không phản hồi 30 giây: `PERMISSION_DENIED`.
- Rút cáp giữa chừng (`ACTION_USB_DEVICE_DETACHED`): huỷ ghi, trả `DISCONNECTED`, xoá mọi state kết nối cũ (không tái dùng device stale).
- Máy in không có bulk OUT: `UNSUPPORTED_DEVICE`.

## Postconditions

- Không leak receiver/connection; lần in tiếp theo không cần xin lại quyền.

## Out of scope

- USB trên iOS (Apple chỉ cho phép phụ kiện MFi — không hỗ trợ).
- USB-serial (FTDI/CH34x) — v2.

## Acceptance criteria (feeds TEST-UAT-*)

- [ ] ACE3 + ICOD: đồng ý quyền → in được ngay lần đầu.
- [ ] Rút/cắm lại cáp → in tiếp được không cần khởi động lại app.
- [ ] Không crash trên Android 12, 14, 15 (targetSdk mới nhất).
- [ ] Hoá đơn raster 50 KB in trọn vẹn, không mất đoạn.
