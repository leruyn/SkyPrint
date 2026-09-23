package com.dcorp.skyprint.core.transport

import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import com.dcorp.skyprint.core.model.PrinterInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Tuần tự hoá lệnh in theo máy in, có retry/timeout/mã lỗi thống nhất
 * (REQ-008, DESIGN-008). Facade [PrinterService] (chưa cài -- gồm cả
 * encode/template) sẽ gọi [print] với byte đã dựng sẵn từ REQ-001/002/009.
 *
 * KHÔNG bắt [CancellationException] thành [PrintResult.Failure] -- app tự
 * huỷ job (`Job.cancel()`) thì lời gọi [print] tự huỷ theo (coroutine
 * convention chuẩn), không giả vờ trả về kết quả bình thường. "Transport
 * đóng sạch" khi bị huỷ (req.md's alternate flow) vẫn đảm bảo vì [attemptOnce]
 * đóng connection trong ngữ cảnh [NonCancellable].
 */
class PrintQueue(
    private val transports: List<PrinterTransport>,
    private val policy: RetryPolicy = RetryPolicy(),
) {
    private val locksGuard = Mutex()
    private val locksByPrinterId = mutableMapOf<String, Mutex>()

    suspend fun print(printer: PrinterInfo, bytes: ByteArray): PrintResult {
        val transport = transports.find { it.kind == printer.kind }
            ?: return PrintResult.Failure(
                PrinterErrorCode.UNSUPPORTED_PLATFORM,
                "Không có transport nào hỗ trợ ${printer.kind} trên nền tảng này.",
            )

        val lock = lockFor(printer.id)
        return lock.withLock {
            try {
                withTimeout(policy.jobTimeoutMs) { attemptWithRetry(transport, printer, bytes) }
            } catch (e: TimeoutCancellationException) {
                PrintResult.Failure(PrinterErrorCode.TIMEOUT, "Quá thời gian in cho phép (${policy.jobTimeoutMs}ms).")
            }
        }
    }

    private suspend fun attemptWithRetry(transport: PrinterTransport, printer: PrinterInfo, bytes: ByteArray): PrintResult {
        val maxAttempts = policy.maxRetries + 1
        var lastFailure: PrintResult.Failure? = null

        for (attempt in 0 until maxAttempts) {
            when (val result = attemptOnce(transport, printer, bytes)) {
                is PrintResult.Success -> return result
                is PrintResult.Failure -> {
                    lastFailure = result
                    val isLastAttempt = attempt == maxAttempts - 1
                    if (!result.code.retryable || isLastAttempt) return result
                    delay(policy.backoffMs.getOrElse(attempt) { policy.backoffMs.lastOrNull() ?: 0L })
                }
            }
        }
        return lastFailure ?: PrintResult.Failure(PrinterErrorCode.UNKNOWN, "Không in được (không rõ lý do).")
    }

    private suspend fun attemptOnce(transport: PrinterTransport, printer: PrinterInfo, bytes: ByteArray): PrintResult {
        var connection: PrinterConnection? = null
        return try {
            connection = transport.open(printer)
            connection.write(bytes)
            PrintResult.Success
        } catch (e: PrinterException) {
            PrintResult.Failure(e.code, e.message ?: "Lỗi in không rõ nguyên nhân.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PrintResult.Failure(PrinterErrorCode.UNKNOWN, "Lỗi không xác định: ${e.message ?: e::class.simpleName}")
        } finally {
            // NonCancellable: job bị huỷ (CancellationException) khi đang ở đây vẫn phải
            // đóng connection thật sự -- gọi close() bình thường lúc đã huỷ sẽ throw ngay
            // mà không chạy gì, vì coroutine kiểm tra huỷ trước mỗi điểm suspend.
            withContext(NonCancellable) {
                try {
                    connection?.close()
                } catch (ignored: Exception) {
                    // Đóng kết nối đã hỏng sẵn throw là bình thường, không ảnh hưởng PrintResult đã có.
                }
            }
        }
    }

    private suspend fun lockFor(printerId: String): Mutex = locksGuard.withLock {
        locksByPrinterId.getOrPut(printerId) { Mutex() }
    }
}
