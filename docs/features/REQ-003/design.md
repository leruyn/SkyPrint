# DESIGN-003 — Discovery đa transport (DESIGN)

> depends_on REQ-003.

## Interfaces

```kotlin
enum class TransportKind { USB, LAN, BT_CLASSIC, BLE }
data class PrinterInfo(val id: String, val name: String, val kind: TransportKind, val address: String, val extras: Map<String, String> = emptyMap())
interface PrinterDiscovery {
  val kind: TransportKind
  fun discover(timeoutMs: Long): Flow<PrinterInfo>   // Dart: Stream<PrinterInfo>
}
object LanProbe { suspend fun probe(host: String, port: Int = 9100, timeoutMs: Long = 3000): Boolean }
```

`id` ổn định: USB = `usb:<vendorId>:<productId>:<serial?>` (deviceName đổi sau mỗi lần cắm), BT = `bt:<MAC>`, BLE = `ble:<MAC|CB-UUID>`, LAN = `lan:<host>:<port>`.

## Components

- `UsbDiscovery` (Android) — `UsbManager.deviceList`, lọc interface class 7.
- `BtClassicDiscovery` (Android) — `bondedDevices`, lọc major class IMAGING/`UNCATEGORIZED` + tên.
- `BleDiscovery` — Kable `Scanner` (KMP) / Swift `CBCentralManager` + Kotlin (Flutter).
- `LanDiscovery` — quét /24 của IP hiện tại, 32 kết nối song song, timeout 300ms/host.
- `CompositeDiscovery` — merge các transport, khử trùng theo `id`.

## Dependencies

- DESIGN-004..007 (id format phải khớp transport).

## Risks / open questions

- Quét subnet có thể bị firewall/mạng doanh nghiệp chặn → luôn cho nhập IP tay.
