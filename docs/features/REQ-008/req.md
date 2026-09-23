# REQ-008 — Hàng đợi lệnh in, timeout, retry, mã lỗi thống nhất (REQ)

## Actor(s)

- **App (nhiều màn hình / nhiều request cùng lúc)**: gửi lệnh in.
- **Thư viện**: tuần tự hoá và báo kết quả.

## Goal

Mọi lệnh in tới cùng một máy in chạy lần lượt, có timeout, retry có giới hạn và kết quả lỗi thống nhất giữa các transport và giữa KMP/Flutter.

## Preconditions

- Có `PrinterInfo` hợp lệ (REQ-003).

## Main flow

1. App: `printer.print(job)` (job = bytes hoặc `ReceiptDocument`).
   System: đưa vào hàng đợi của máy in đó (một queue/máy in, các máy in khác nhau chạy song song).
2. System: lấy job → mở transport → ghi → đóng → trả `PrintResult.Success`.
3. App: nhận kết quả qua `suspend`/`Future`.

## Alternate / exception flows

- Lỗi tạm thời (`DISCONNECTED`, `CONNECT_TIMEOUT`): retry tối đa N lần (mặc định 2) với backoff; lỗi vĩnh viễn (`PERMISSION_DENIED`, `UNSUPPORTED_DEVICE`, `INVALID_DOCUMENT`) không retry.
- Job vượt tổng timeout: `TIMEOUT`.
- App huỷ job (coroutine cancel / `cancel()`): job bị huỷ, transport đóng sạch.

## Postconditions

- Không bao giờ có 2 job ghi xen byte vào cùng 1 máy in.
- Mã lỗi thuộc tập cố định `PrinterErrorCode` giống nhau ở KMP và Flutter.

## Out of scope

- Lưu hàng đợi bền vững qua lần khởi động app (in lại sau crash) — v2.

## Acceptance criteria (feeds TEST-UAT-*)

- [ ] Bấm "In" 5 lần thật nhanh → 5 hoá đơn in đủ, không trộn.
- [ ] Rút máy in → app nhận lỗi rõ ràng (không treo), cắm lại in tiếp được.
- [ ] Cùng tình huống lỗi, KMP và Flutter trả cùng `PrinterErrorCode`.
