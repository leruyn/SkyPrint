package com.dcorp.skyprint.core.ble

/**
 * Cấu hình BLE (REQ-007, DESIGN-007). [serviceUuid]/[characteristicUuid]:
 * cấu hình tường minh theo máy in cụ thể, ưu tiên cao nhất. [knownPairs]:
 * danh sách UUID phổ biến của máy in nhiệt giá rẻ (dò theo thứ tự khai,
 * xem [BleDefaults]). Không tìm thấy cặp nào khớp -> lấy characteristic
 * ghi được ĐẦU TIÊN tìm thấy (xem [CharacteristicResolver]).
 */
data class BleConfig(
    val serviceUuid: String? = null,
    val characteristicUuid: String? = null,
    val knownPairs: List<Pair<String, String>> = BleDefaults.KNOWN_PAIRS,
    val connectTimeoutMs: Long = 10_000,
    /** iOS không có API tương đương `requestMtu` -- dùng `maximumWriteValueLengthForType` (Kable tự trừu tượng hoá 2 nền tảng). */
    val requestMtu: Int = 512,
    /** `WriteType.WithoutResponse` không có ack -- nghỉ giữa các gói tránh tràn buffer máy in giá rẻ. */
    val withoutResponsePacingMs: Long = 10,
)

/**
 * UUID service/characteristic ghi được của các dòng máy in nhiệt BLE giá rẻ
 * phổ biến (thường thấy trên máy in mini/cầm tay Trung Quốc) -- KHÔNG phải
 * danh sách đầy đủ, chỉ là điểm khởi đầu hợp lý; máy in cụ thể của khách
 * nên cấu hình [BleConfig.serviceUuid]/[BleConfig.characteristicUuid]
 * tường minh khi biết chắc.
 */
object BleDefaults {
    val KNOWN_PAIRS: List<Pair<String, String>> = listOf(
        // Phổ biến nhất trên máy in nhiệt BLE giá rẻ (module BLE Trung Quốc thông dụng).
        "0000ff00-0000-1000-8000-00805f9b34fb" to "0000ff02-0000-1000-8000-00805f9b34fb",
        "000018f0-0000-1000-8000-00805f9b34fb" to "00002af1-0000-1000-8000-00805f9b34fb",
        // Biến thể giống Nordic UART Service.
        "e7810a71-73ae-499d-8c15-faa9aef0c3f2" to "bef8d6c9-9c21-4c9e-b632-bd58c1009f9f",
        // Module BLE hãng Bluetooth SIG cấp UUID riêng, hay gặp trên máy in cầm tay.
        "49535343-fe7d-4ae5-8fa9-9fafd205e455" to "49535343-8841-43f4-a8d4-ecbe34729bb3",
    )
}
