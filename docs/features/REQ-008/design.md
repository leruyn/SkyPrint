# DESIGN-008 — Contracts, hàng đợi, lỗi thống nhất (DESIGN)

> depends_on REQ-008. Các DESIGN transport phụ thuộc contract ở đây.

## Interfaces

```kotlin
interface PrinterConnection { suspend fun write(bytes: ByteArray); suspend fun close() }
interface PrinterTransport { val kind: TransportKind; suspend fun open(printer: PrinterInfo): PrinterConnection }
class PrinterException(val code: PrinterErrorCode, message: String, cause: Throwable? = null) : Exception(message, cause)
enum class PrinterErrorCode(val retryable: Boolean) { /* xem docs/architecture.md */ }
sealed interface PrintResult { data object Success : PrintResult; data class Failure(val code: PrinterErrorCode, val message: String) : PrintResult }

class PrintQueue(transports: List<PrinterTransport>, policy: RetryPolicy = RetryPolicy(), clock: Clock = Clock.System) {
  suspend fun print(printer: PrinterInfo, bytes: ByteArray): PrintResult
}
data class RetryPolicy(val maxRetries: Int = 2, val backoffMs: List<Long> = listOf(500, 1500), val jobTimeoutMs: Long = 30_000)
```

Dart: tương đương, `Future<PrintResult> print(...)`.

## Components

- `PrintQueue` — `Map<printerId, Mutex>` (KMP) / chuỗi `Future` theo id (Dart); retry theo `retryable`; `withTimeout(jobTimeoutMs)`; `finally { connection.close() }`.
- `PrinterService` (facade cho app) — `print(printer, document, mode)` = encode (DESIGN-001/002) + queue.

## Dependencies

- `kotlinx-coroutines-core`. Dart: không.

## Risks / open questions

- Keep-alive kết nối (giữ mở giữa các job) — v1 mở/đóng mỗi job cho đơn giản và đúng; đo latency ở TEST-SIT rồi quyết.
