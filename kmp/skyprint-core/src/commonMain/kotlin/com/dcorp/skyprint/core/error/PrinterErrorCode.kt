package com.dcorp.skyprint.core.error

/**
 * Mã lỗi thống nhất giữa mọi transport và cả hai bản KMP/Dart (DESIGN-008,
 * xem bảng đầy đủ + [retryable] ở docs/architecture.md của repo skyprint).
 * Encoder/document (REQ-001) chỉ dùng [INVALID_DOCUMENT] và [INVALID_TEMPLATE]
 * -- các mã còn lại thuộc transport/queue (REQ-004..008), khai đủ ở đây để
 * component nào cũng tham chiếu cùng một enum, không rải rác định nghĩa lỗi
 * theo từng module.
 */
enum class PrinterErrorCode(val retryable: Boolean) {
    INVALID_DOCUMENT(retryable = false),
    INVALID_TEMPLATE(retryable = false),
    PERMISSION_REQUIRED(retryable = false),
    PERMISSION_DENIED(retryable = false),
    UNSUPPORTED_PLATFORM(retryable = false),
    UNSUPPORTED_DEVICE(retryable = false),
    NOT_FOUND(retryable = false),
    ADAPTER_OFF(retryable = false),
    CONNECT_TIMEOUT(retryable = true),
    HOST_UNREACHABLE(retryable = true),
    DISCONNECTED(retryable = true),
    WRITE_FAILED(retryable = true),
    TIMEOUT(retryable = false),
    CANCELLED(retryable = false),
    UNKNOWN(retryable = false),
}

/** Ném khi encoder/template không thể tạo ra byte hợp lệ -- không phải lỗi transport. */
class PrinterException(
    val code: PrinterErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
