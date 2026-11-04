package com.dcorp.skyprint.raster

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** UNIT-053-07/08: ảnh xám -> đơn sắc -> lệnh raster ESC/POS. */
class RasterTest {
    private fun gray(w: Int, h: Int, v: Int) = GrayBitmap(w, h, ByteArray(w * h) { v.toByte() })

    // UNIT-053-07
    @Test
    fun `toan trang thi khong co cham toan den thi moi bit hop le la 1`() {
        val white = MonoConverter.toMono(gray(10, 3, 255))
        assertEquals(2, white.rowBytes); assertTrue(white.data.all { it == 0.toByte() })
        val black = MonoConverter.toMono(gray(10, 3, 0))
        // 10 điểm = 1 byte đầy + 2 bit cao của byte thứ hai (phần đệm phải trắng)
        assertEquals(listOf(0xFF.toByte(), 0xC0.toByte()), black.data.take(2))
        assertEquals(6, black.data.size)
    }

    @Test
    fun `xam trung binh ra khoang mot nua cham va ket qua on dinh`() {
        val a = MonoConverter.toMono(gray(64, 64, 128)); val b = MonoConverter.toMono(gray(64, 64, 128))
        assertContentEquals(a.data, b.data)
        val dots = a.data.sumOf { (it.toInt() and 0xFF).countOneBits() }
        assertTrue(dots in (64 * 64 * 4 / 10)..(64 * 64 * 6 / 10), "dots=$dots")
    }

    // UNIT-053-08
    @Test
    fun `lenh raster co ESC GS v 0 header dung chia dai 256 hang ket thuc cat giay`() {
        val mono = MonoConverter.toMono(gray(512, 700, 0))
        val bytes = EscPosRaster.encode(mono)
        assertEquals(listOf(0x1B.toByte(), 0x40.toByte()), bytes.take(2))
        // dải 1: 256 hàng, 64 byte/hàng
        assertContentEquals(byteArrayOf(0x1D, 0x76, 0x30, 0x00, 64, 0, 0x00, 0x01), bytes.copyOfRange(2, 10))
        val firstBandEnd = 10 + 64 * 256
        assertContentEquals(byteArrayOf(0x1D, 0x76, 0x30, 0x00, 64, 0, 0x00, 0x01), bytes.copyOfRange(firstBandEnd, firstBandEnd + 8))
        val secondBandEnd = firstBandEnd + 8 + 64 * 256
        // dải 3: 700 - 512 = 188 hàng
        assertContentEquals(byteArrayOf(0x1D, 0x76, 0x30, 0x00, 64, 0, 188.toByte(), 0x00), bytes.copyOfRange(secondBandEnd, secondBandEnd + 8))
        assertEquals(secondBandEnd + 8 + 64 * 188 + 3 /* ESC d 3 */ + 4 /* GS V B 0 */, bytes.size)
        assertEquals(listOf(0x1B.toByte(), 0x64, 3, 0x1D.toByte(), 0x56, 0x42, 0x00), bytes.takeLast(7))
    }

    @Test
    fun `anh thap hon mot dai van hop le`() {
        val bytes = EscPosRaster.encode(MonoConverter.toMono(gray(16, 5, 0)), feedLines = 2)
        assertContentEquals(byteArrayOf(0x1D, 0x76, 0x30, 0x00, 2, 0, 5, 0), bytes.copyOfRange(2, 10))
        assertEquals(listOf(0x1B.toByte(), 0x64, 2), bytes.takeLast(7).take(3))
    }
}
