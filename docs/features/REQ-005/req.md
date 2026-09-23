# REQ-005 — Transport LAN TCP 9100 (Android + iOS) (REQ)

## Actor(s)

- **Thu ngân / bếp**: in tới máy in mạng (quầy, bếp).
- **Thư viện**: gửi byte qua TCP socket.

## Goal

In tới máy in Ethernet/Wi-Fi qua raw TCP cổng 9100 (JetDirect) trên cả Android và iOS với cùng một API.

## Preconditions

- Thiết bị và máy in cùng mạng, IP máy in đã biết (từ REQ-003 hoặc nhập tay).
- iOS: app khai `NSLocalNetworkUsageDescription` (Local Network privacy, iOS 14+).

## Main flow

1. System: mở socket tới `host:port` (mặc định 9100), timeout kết nối 3 giây.
2. System: ghi toàn bộ byte, flush, timeout ghi 10 giây.
3. System: đóng socket (hoặc giữ theo keep-alive).

## Alternate / exception flows

- Không kết nối được: `CONNECT_TIMEOUT` / `HOST_UNREACHABLE`.
- Mất kết nối khi đang ghi: `DISCONNECTED`; hàng đợi (REQ-008) quyết định retry.
- iOS người dùng từ chối Local Network: `PERMISSION_DENIED` kèm hướng dẫn.

## Postconditions

- Socket luôn được đóng ở mọi nhánh lỗi.

## Out of scope

- Máy in qua IPP/AirPrint, driver OS.
- Đọc trạng thái máy in (DLE EOT) — v2.

## Acceptance criteria (feeds TEST-UAT-*)

- [ ] Android và iPhone in được tới cùng máy in LAN.
- [ ] Tắt máy in → báo lỗi trong ≤ 3 giây, app không treo.
- [ ] 2 thiết bị in đồng thời tới 1 máy in → cả 2 hoá đơn in đủ, không trộn.
