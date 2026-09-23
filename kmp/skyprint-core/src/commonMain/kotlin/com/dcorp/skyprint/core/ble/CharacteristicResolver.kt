package com.dcorp.skyprint.core.ble

/**
 * Đại diện thuần cho một characteristic ghi được, tách khỏi kiểu thật của
 * Kable ([com.juul.kable.DiscoveredCharacteristic]) để [CharacteristicResolver]
 * test được mà không cần thiết bị BLE thật (REQ-007).
 */
data class BleCharacteristicInfo(
    val serviceUuid: String,
    val characteristicUuid: String,
    val writableWithResponse: Boolean,
    val writableWithoutResponse: Boolean,
)

/**
 * Chọn characteristic để ghi byte ESC/POS (REQ-007, DESIGN-007) theo thứ
 * tự ưu tiên: cấu hình tường minh -> `knownPairs` (đúng thứ tự khai) ->
 * characteristic ghi được đầu tiên tìm thấy (ưu tiên có ack `WithResponse`
 * hơn `WithoutResponse` -- chắc chắn hơn khi chưa biết máy in cụ thể).
 */
internal object CharacteristicResolver {
    fun resolve(characteristics: List<BleCharacteristicInfo>, config: BleConfig): BleCharacteristicInfo? {
        fun find(serviceUuid: String, characteristicUuid: String): BleCharacteristicInfo? =
            characteristics.firstOrNull {
                it.serviceUuid.equals(serviceUuid, ignoreCase = true) && it.characteristicUuid.equals(characteristicUuid, ignoreCase = true)
            }

        val configuredService = config.serviceUuid
        val configuredCharacteristic = config.characteristicUuid
        if (configuredService != null && configuredCharacteristic != null) {
            find(configuredService, configuredCharacteristic)?.let { return it }
        }

        for ((serviceUuid, characteristicUuid) in config.knownPairs) {
            find(serviceUuid, characteristicUuid)?.let { return it }
        }

        return characteristics.firstOrNull { it.writableWithResponse }
            ?: characteristics.firstOrNull { it.writableWithoutResponse }
    }
}
