# DESIGN-006 — Transport Bluetooth Classic SPP (Android) (DESIGN)

> depends_on REQ-006, DESIGN-008.

## Interfaces

```kotlin
class BtClassicPrinterTransport(context: Context, config: BtConfig = BtConfig()) : PrinterTransport
data class BtConfig(val chunkSize: Int = 1024, val interChunkDelayMs: Long = 0, val insecureFallback: Boolean = true)
```

## Components

- `BtConnection` — `createRfcommSocketToServiceRecord(SPP)`; thất bại → `createInsecureRfcommSocketToServiceRecord`; `connect()` chạy trên `Dispatchers.IO` với `withTimeout` (đóng socket khi timeout để giải phóng `connect()` đang block).
- Kiểm tra quyền `BLUETOOTH_CONNECT` (API 31+) trước mọi lệnh.

## Dependencies

- Không thư viện ngoài.

## Risks / open questions

- Máy in rẻ có buffer nhỏ → `interChunkDelayMs` cấu hình được, đo ở TEST-SIT.
