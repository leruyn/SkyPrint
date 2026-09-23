# DESIGN-007 — Transport BLE (DESIGN)

> depends_on REQ-007, DESIGN-008.

## Interfaces

```kotlin
class BlePrinterTransport(config: BleConfig = BleConfig()) : PrinterTransport     // commonMain (Kable)
data class BleConfig(
  val serviceUuid: String? = null, val characteristicUuid: String? = null,
  val knownPairs: List<Pair<String, String>> = BleDefaults.KNOWN,
  val requestMtu: Int = 512, val withoutResponsePacingMs: Long = 10,
)
internal object CharacteristicResolver { fun resolve(services: List<GattService>, config: BleConfig): GattCharacteristic? }  // pure, test được
```

Flutter: Android dùng lại `BlePrinterTransport` của KMP qua plugin; iOS Swift `BlePrinter` (`CBCentralManager`/`CBPeripheral`) với cùng thứ tự resolve UUID.

## Components

- `CharacteristicResolver` — thứ tự: cấu hình → `knownPairs` → characteristic đầu tiên có write/writeWithoutResponse.
- `BleConnection` — chunk = `mtu - 3`; ưu tiên `writeWithResponse` nếu có (chắc chắn), không thì without-response + pacing.

## Dependencies

- `com.juul.kable:kable-core`.

## Risks / open questions

- iOS không cho `requestMtu`; dùng `maximumWriteValueLength(for:)`.
- Android `GATT 133`: retry kết nối 1 lần sau `close()` + delay 300ms.
