package com.dcorp.skyprint.core.transport

import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * TEST-UNIT-004a -- viết trước khi có [ChunkedWriter] (đỏ). Tách riêng
 * khỏi USB thật vì đây là chỗ dễ có bug nhất trong bản gốc DantSu/legacy
 * đã review đầu phiên (bulkTransfer trả 0 -> vòng lặp không bao giờ kết
 * thúc) -- kiểm bằng test thuần, không cần USB thật hay Android.
 */
class ChunkedWriterTest {

    @Test
    fun `du lieu nho hon 1 chunk ghi dung 1 lan goi transfer`() = runTest {
        val calls = mutableListOf<Pair<Int, Int>>()
        ChunkedWriter.write(byteArrayOf(1, 2, 3), chunkSize = 10) { offset, length ->
            calls += offset to length
            length
        }
        assertEquals(listOf(0 to 3), calls)
    }

    @Test
    fun `du lieu chia dung nhieu chunk theo dung offset`() = runTest {
        val calls = mutableListOf<Pair<Int, Int>>()
        val data = ByteArray(10) { it.toByte() }
        ChunkedWriter.write(data, chunkSize = 4) { offset, length ->
            calls += offset to length
            length
        }
        assertEquals(listOf(0 to 4, 4 to 4, 8 to 2), calls)
    }

    @Test
    fun `transfer ghi it hon length van tiep tuc tu dung vi tri con lai`() = runTest {
        val sent = mutableListOf<Byte>()
        val data = ByteArray(10) { it.toByte() }
        ChunkedWriter.write(data, chunkSize = 10) { offset, length ->
            // giả lập bulkTransfer chỉ gửi được 3 byte mỗi lần dù xin nhiều hơn
            val n = minOf(3, length)
            sent += data.copyOfRange(offset, offset + n).toList()
            n
        }
        assertContentEquals(data.toList(), sent)
    }

    @Test
    fun `transfer tra ve 0 nem WRITE_FAILED khong lap vo han`() = runTest {
        var callCount = 0
        val ex = assertFailsWith<PrinterException> {
            ChunkedWriter.write(byteArrayOf(1, 2, 3), chunkSize = 10) { _, _ ->
                callCount++
                0
            }
        }
        assertEquals(PrinterErrorCode.WRITE_FAILED, ex.code)
        assertEquals(1, callCount, "phải dừng ngay lần đầu transferred<=0, không lặp vô hạn")
    }

    @Test
    fun `transfer tra ve am nem WRITE_FAILED`() = runTest {
        val ex = assertFailsWith<PrinterException> {
            ChunkedWriter.write(byteArrayOf(1), chunkSize = 10) { _, _ -> -1 }
        }
        assertEquals(PrinterErrorCode.WRITE_FAILED, ex.code)
    }

    @Test
    fun `du lieu rong khong goi transfer lan nao`() = runTest {
        var called = false
        ChunkedWriter.write(ByteArray(0), chunkSize = 10) { _, _ -> called = true; 0 }
        assertEquals(false, called)
    }
}
