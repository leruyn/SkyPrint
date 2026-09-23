package com.dcorp.skyprint.core.raster

import com.dcorp.skyprint.core.model.MonoBitmap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * TEST-UNIT-002b -- viết SAU khi có [RasterPacker]/[Dither] (lệch thứ tự
 * TDD chuẩn, như [com.dcorp.skyprint.core.encode.CapabilityFilterTest] --
 * ghi nhận công khai thay vì giấu đi).
 */
class RasterPackerTest {
    @Test
    fun `dong goi anh nho trong 1 dai thanh 1 lenh GS v 0`() {
        // 8x2 dot, tất cả bit = 1 -> 1 byte/dòng (widthBytes=1), 2 dòng.
        val bitmap = MonoBitmap(width = 8, height = 2, bits = byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
        val bytes = RasterPacker.toGsV0(bitmap)

        val expectedHeader = byteArrayOf(0x1D, 0x76, 0x30, 0x00, 1, 0, 2, 0) // xL=1,xH=0, yL=2,yH=0
        assertContentEquals(expectedHeader, bytes.copyOfRange(0, 8))
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), bytes.copyOfRange(8, 10))
        assertEquals(10, bytes.size)
    }

    @Test
    fun `anh cao hon bandHeight duoc chia thanh nhieu lenh GS v 0`() {
        val height = 10
        val bandHeight = 4
        val bits = ByteArray(height) { it.toByte() } // 1 byte/dòng (width=8)
        val bitmap = MonoBitmap(width = 8, height = height, bits = bits)

        val bytes = RasterPacker.toGsV0(bitmap, bandHeight = bandHeight)

        // 3 dải: 4 + 4 + 2 dòng, mỗi dải 8 byte header + N byte dữ liệu
        val band1 = byteArrayOf(0x1D, 0x76, 0x30, 0x00, 1, 0, 4, 0) + bits.copyOfRange(0, 4)
        val band2 = byteArrayOf(0x1D, 0x76, 0x30, 0x00, 1, 0, 4, 0) + bits.copyOfRange(4, 8)
        val band3 = byteArrayOf(0x1D, 0x76, 0x30, 0x00, 1, 0, 2, 0) + bits.copyOfRange(8, 10)
        assertContentEquals(band1 + band2 + band3, bytes)
    }

    @Test
    fun `anh rong tra ve mang byte rong`() {
        val bitmap = MonoBitmap(width = 8, height = 0, bits = ByteArray(0))
        assertEquals(0, RasterPacker.toGsV0(bitmap).size)
    }

    @Test
    fun `width khong chia het 8 lam tron len byte tiep theo`() {
        // width=9 dot -> 2 byte/dòng (widthBytes = ceil(9/8) = 2)
        val bitmap = MonoBitmap(width = 9, height = 1, bits = byteArrayOf(0x00, 0x00))
        val bytes = RasterPacker.toGsV0(bitmap)
        assertEquals(2, bytes[4].toInt(), "xL phải phản ánh đúng số byte/dòng đã làm tròn")
    }
}

class DitherTest {
    @Test
    fun `pixel toi hon nguong thanh bit 1 sang hon thanh bit 0`() {
        // 2x1: pixel 0 (đen tuyệt đối) và 255 (trắng tuyệt đối)
        val gray = byteArrayOf(0, 255.toByte())
        val bitmap = Dither.threshold(gray, width = 2, height = 1, level = 128)
        // bit cao nhất (MSB) = pixel đầu tiên = phải là 1; bit kế = 0
        assertEquals(0x80, bitmap.bits[0].toInt() and 0xFF)
    }

    @Test
    fun `sai kich thuoc gray nem IllegalArgumentException`() {
        try {
            Dither.threshold(ByteArray(3), width = 2, height = 2)
            throw AssertionError("phải ném IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // đúng như mong đợi
        }
    }
}
