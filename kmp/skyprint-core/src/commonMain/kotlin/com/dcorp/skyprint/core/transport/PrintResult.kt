package com.dcorp.skyprint.core.transport

import com.dcorp.skyprint.core.error.PrinterErrorCode

/** Kết quả một lệnh in (REQ-008) -- KMP và Dart cùng hình dạng này để app dùng chung logic xử lý kết quả. */
sealed interface PrintResult {
    data object Success : PrintResult
    data class Failure(val code: PrinterErrorCode, val message: String) : PrintResult
}

/**
 * [maxRetries]: số lần thử LẠI sau lần đầu (tổng số lần thử = maxRetries + 1).
 * [backoffMs]: thời gian chờ trước mỗi lần thử lại, theo thứ tự; hết danh
 * sách thì lặp lại giá trị cuối. [jobTimeoutMs]: tổng thời gian tối đa cho
 * CẢ job (gồm mọi lần retry) -- vượt quá thì huỷ và trả [PrinterErrorCode.TIMEOUT].
 */
data class RetryPolicy(
    val maxRetries: Int = 2,
    val backoffMs: List<Long> = listOf(500, 1500),
    val jobTimeoutMs: Long = 30_000,
)
