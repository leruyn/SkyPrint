# DESIGN-005 — Transport LAN TCP 9100 (DESIGN)

> depends_on REQ-005, DESIGN-008.

## Interfaces

```kotlin
class LanPrinterTransport(config: LanConfig = LanConfig()) : PrinterTransport   // commonMain, Android + iOS
data class LanConfig(val connectTimeoutMs: Long = 3000, val writeTimeoutMs: Long = 10_000)
```

Dart: `class LanPrinterTransport implements PrinterTransport` dùng `Socket.connect(host, port, timeout:)` (`dart:io`, không native).

## Components

- KMP: `ktor-network` `aSocket(SelectorManager(Dispatchers.IO)).tcp().connect(host, port)` + `openWriteChannel(autoFlush=false)`.
- Map lỗi: timeout → `CONNECT_TIMEOUT`; `ConnectException`/`SocketException` host → `HOST_UNREACHABLE`; lỗi khi ghi → `DISCONNECTED`.

## Dependencies

- KMP: `io.ktor:ktor-network`. Dart: không.
- Test SIT: fake printer server (TCP listener ghi byte ra file) chạy trong test.

## Risks / open questions

- iOS Local Network prompt chỉ hiện lần đầu; app phải khai `NSLocalNetworkUsageDescription` — ghi rõ trong README tích hợp.
