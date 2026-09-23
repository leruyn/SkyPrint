# DESIGN-004 — Transport USB (Android) (DESIGN)

> depends_on REQ-004, DESIGN-008 (contracts).

## Interfaces

```kotlin
class UsbPrinterTransport(context: Context, config: UsbConfig = UsbConfig()) : PrinterTransport {
  override val kind = TransportKind.USB
  override suspend fun open(printer: PrinterInfo): PrinterConnection
}
data class UsbConfig(val chunkSize: Int = 4096, val writeTimeoutMs: Int = 5000, val permissionTimeoutMs: Long = 30_000)
internal interface UsbPermissionGate { suspend fun ensure(device: UsbDevice): Boolean }   // tách để test
```

## Components

- `UsbPermissionGate` impl — `PendingIntent.getBroadcast(ctx, 0, Intent(ACTION).setPackage(pkg), FLAG_MUTABLE)`; `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`; sau broadcast đọc `usbManager.hasPermission(device)`; `withTimeout`.
- `UsbConnection` — claim interface class 7, bulk OUT; `write` chia chunk, `transferred <= 0` → `WRITE_FAILED`; `close` release + close idempotent.
- Theo dõi `ACTION_USB_DEVICE_DETACHED` → connection đang mở chuyển `DISCONNECTED`.

## Dependencies

- `androidx.core` (ContextCompat). Không dùng thư viện USB ngoài.

## Risks / open questions

- Một số ROM (Sunmi, iMin) có máy in nội bộ qua service riêng, không qua USB class 7 → ngoài phạm vi v1.
