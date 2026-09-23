package com.dcorp.skyprint.core.transport

import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * TEST-UNIT-008 -- viết trước khi có [PrintQueue] (đỏ), theo req.md's
 * acceptance criteria. Fake transport/connection để điều khiển chính xác
 * kịch bản (thành công/lỗi tạm thời/lỗi vĩnh viễn/timeout/tuần tự hoá).
 */
class PrintQueueTest {

    private fun printer(id: String = "usb:1:1", kind: TransportKind = TransportKind.USB) =
        PrinterInfo(id = id, name = "Test", kind = kind, address = id)

    /** Transport giả lập: mỗi lần open() gọi [onOpen] để quyết định thành công hay ném lỗi gì. */
    private class FakeTransport(
        override val kind: TransportKind = TransportKind.USB,
        val writes: MutableList<String> = mutableListOf(),
        val onOpen: suspend (attempt: Int) -> Unit = {},
    ) : PrinterTransport {
        var attempts = 0
        var closeCount = 0

        override suspend fun open(printer: PrinterInfo): PrinterConnection {
            val current = attempts++
            onOpen(current)
            return object : PrinterConnection {
                override suspend fun write(bytes: ByteArray) { writes += bytes.decodeToString() }
                override suspend fun close() { closeCount++ }
            }
        }
    }

    @Test
    fun `in thanh cong tra Success va dong ket noi`() = runTest {
        val transport = FakeTransport()
        val queue = PrintQueue(listOf(transport))

        val result = queue.print(printer(), "hello".encodeToByteArray())

        assertEquals(PrintResult.Success, result)
        assertEquals(listOf("hello"), transport.writes)
        assertEquals(1, transport.closeCount)
    }

    @Test
    fun `khong co transport nao khop kind tra UNSUPPORTED_PLATFORM`() = runTest {
        val queue = PrintQueue(listOf(FakeTransport(kind = TransportKind.LAN)))
        val result = queue.print(printer(kind = TransportKind.BLE), "x".encodeToByteArray())
        val failure = assertIs<PrintResult.Failure>(result)
        assertEquals(PrinterErrorCode.UNSUPPORTED_PLATFORM, failure.code)
    }

    @Test
    fun `loi tam thoi duoc retry va thanh cong o lan thu 2`() = runTest {
        val transport = FakeTransport(onOpen = { attempt ->
            if (attempt == 0) throw PrinterException(PrinterErrorCode.CONNECT_TIMEOUT, "timeout")
        })
        val queue = PrintQueue(listOf(transport), RetryPolicy(maxRetries = 2, backoffMs = listOf(10)))

        val result = queue.print(printer(), "x".encodeToByteArray())

        assertEquals(PrintResult.Success, result)
        assertEquals(2, transport.attempts, "phải mở lại kết nối ở mỗi lần retry")
    }

    @Test
    fun `loi tam thoi that bai het so lan retry tra dung ma loi cuoi`() = runTest {
        val transport = FakeTransport(onOpen = { throw PrinterException(PrinterErrorCode.DISCONNECTED, "mat ket noi") })
        val queue = PrintQueue(listOf(transport), RetryPolicy(maxRetries = 2, backoffMs = listOf(1, 1)))

        val result = queue.print(printer(), "x".encodeToByteArray())

        val failure = assertIs<PrintResult.Failure>(result)
        assertEquals(PrinterErrorCode.DISCONNECTED, failure.code)
        assertEquals(3, transport.attempts, "1 lần đầu + 2 lần retry = 3 lần thử")
    }

    @Test
    fun `loi vinh vien khong duoc retry`() = runTest {
        val transport = FakeTransport(onOpen = { throw PrinterException(PrinterErrorCode.PERMISSION_DENIED, "tu choi") })
        val queue = PrintQueue(listOf(transport), RetryPolicy(maxRetries = 2))

        val result = queue.print(printer(), "x".encodeToByteArray())

        val failure = assertIs<PrintResult.Failure>(result)
        assertEquals(PrinterErrorCode.PERMISSION_DENIED, failure.code)
        assertEquals(1, transport.attempts, "lỗi không retryable chỉ thử đúng 1 lần")
    }

    @Test
    fun `loi khong phai PrinterException duoc coi la UNKNOWN khong crash`() = runTest {
        val transport = FakeTransport(onOpen = { throw IllegalStateException("bug lạ") })
        val queue = PrintQueue(listOf(transport))

        val result = queue.print(printer(), "x".encodeToByteArray())

        val failure = assertIs<PrintResult.Failure>(result)
        assertEquals(PrinterErrorCode.UNKNOWN, failure.code)
    }

    @Test
    fun `vuot tong thoi gian cho phep tra TIMEOUT`() = runTest {
        val transport = FakeTransport(onOpen = { delay(1_000) })
        val queue = PrintQueue(listOf(transport), RetryPolicy(jobTimeoutMs = 50))

        val result = queue.print(printer(), "x".encodeToByteArray())

        val failure = assertIs<PrintResult.Failure>(result)
        assertEquals(PrinterErrorCode.TIMEOUT, failure.code)
    }

    @Test
    fun `2 lenh in cung mot may in khong bao gio chong lan ket noi`() = runTest {
        var active = 0
        var maxActive = 0
        val transport = FakeTransport(onOpen = {
            active++
            maxActive = maxOf(maxActive, active)
            delay(20) // đủ lâu để job kia có cơ hội chen ngang nếu queue không khoá theo printer id
            active--
        })
        val queue = PrintQueue(listOf(transport))
        val samePrinter = printer(id = "usb:1:1")

        val job1 = async { queue.print(samePrinter, "AAA".encodeToByteArray()) }
        val job2 = async { queue.print(samePrinter, "BBB".encodeToByteArray()) }
        job1.await(); job2.await()

        assertEquals(1, maxActive, "không bao giờ có 2 kết nối cùng lúc tới cùng 1 máy in")
        assertEquals(listOf("AAA", "BBB"), transport.writes, "job vào trước phải ghi xong trước job vào sau")
    }

    @Test
    fun `2 lenh in khac may in chay song song khong cho nhau`() = runTest {
        var maxActive = 0
        var active = 0
        val transport = FakeTransport(onOpen = {
            active++
            maxActive = maxOf(maxActive, active)
            delay(20)
            active--
        })
        val queue = PrintQueue(listOf(transport))

        val job1 = async { queue.print(printer(id = "usb:1:1"), "A".encodeToByteArray()) }
        val job2 = async { queue.print(printer(id = "usb:2:2"), "B".encodeToByteArray()) }
        job1.await(); job2.await()

        assertEquals(2, maxActive, "2 máy in khác nhau không cần chờ nhau")
        assertEquals(setOf("A", "B"), transport.writes.toSet())
    }
}
