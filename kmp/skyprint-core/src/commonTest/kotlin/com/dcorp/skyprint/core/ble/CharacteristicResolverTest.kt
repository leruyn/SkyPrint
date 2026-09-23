package com.dcorp.skyprint.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * TEST-UNIT-007b -- [CharacteristicResolver] (REQ-007). Dùng
 * [BleCharacteristicInfo] thuần (không phải object Kable thật -- BLE thật
 * cần thiết bị, không có cách giả lập tin cậy như USB/LAN) để test đúng
 * PHẦN LOGIC dễ sai nhất của transport này: thứ tự ưu tiên chọn
 * characteristic.
 */
class CharacteristicResolverTest {

    private fun info(service: String, char: String, withResponse: Boolean = false, withoutResponse: Boolean = false) =
        BleCharacteristicInfo(service, char, withResponse, withoutResponse)

    @Test
    fun `uu tien cau hinh tuong minh neu co`() {
        val configured = info("svc-1", "char-1", withResponse = true)
        val known = info("svc-2", "char-2", withResponse = true)
        val config = BleConfig(serviceUuid = "svc-1", characteristicUuid = "char-1", knownPairs = listOf("svc-2" to "char-2"))

        val result = CharacteristicResolver.resolve(listOf(known, configured), config)
        assertEquals(configured, result)
    }

    @Test
    fun `cau hinh tuong minh khong khop thi roi xuong known pairs`() {
        val known = info("svc-2", "char-2", withResponse = true)
        val config = BleConfig(serviceUuid = "svc-khong-ton-tai", characteristicUuid = "char-khong-ton-tai", knownPairs = listOf("svc-2" to "char-2"))

        assertEquals(known, CharacteristicResolver.resolve(listOf(known), config))
    }

    @Test
    fun `known pairs dung dung thu tu khai bao`() {
        val first = info("svc-a", "char-a", withResponse = true)
        val second = info("svc-b", "char-b", withResponse = true)
        val config = BleConfig(knownPairs = listOf("svc-b" to "char-b", "svc-a" to "char-a"))

        // Cả 2 đều có mặt -- phải chọn theo THỨ TỰ khai trong knownPairs (svc-b trước), không phải thứ tự trong danh sách characteristics.
        assertEquals(second, CharacteristicResolver.resolve(listOf(first, second), config))
    }

    @Test
    fun `khong khop known pair nao thi lay characteristic ghi duoc dau tien uu tien withResponse`() {
        val onlyWithoutResponse = info("svc-x", "char-x", withoutResponse = true)
        val withResponse = info("svc-y", "char-y", withResponse = true)
        val config = BleConfig(knownPairs = emptyList())

        val result = CharacteristicResolver.resolve(listOf(onlyWithoutResponse, withResponse), config)
        assertEquals(withResponse, result)
    }

    @Test
    fun `khong co withResponse thi lay withoutResponse`() {
        val onlyWithoutResponse = info("svc-x", "char-x", withoutResponse = true)
        val config = BleConfig(knownPairs = emptyList())

        assertEquals(onlyWithoutResponse, CharacteristicResolver.resolve(listOf(onlyWithoutResponse), config))
    }

    @Test
    fun `khong co characteristic nao ghi duoc tra ve null`() {
        val readOnly = info("svc-z", "char-z")
        val config = BleConfig(knownPairs = emptyList())

        assertNull(CharacteristicResolver.resolve(listOf(readOnly), config))
    }

    @Test
    fun `danh sach rong tra ve null`() {
        assertNull(CharacteristicResolver.resolve(emptyList(), BleConfig()))
    }

    @Test
    fun `so sanh UUID khong phan biet hoa thuong`() {
        val configured = info("SVC-1", "CHAR-1", withResponse = true)
        val config = BleConfig(serviceUuid = "svc-1", characteristicUuid = "char-1")

        assertEquals(configured, CharacteristicResolver.resolve(listOf(configured), config))
    }
}
