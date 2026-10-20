package com.dcorp.skyprint.registry

enum class RegistryErrorCode { INVALID_INPUT, NOT_FOUND }

/** [message] là câu tiếng Việt hiển thị thẳng cho người dùng (giữ nguyên như bản trong app). */
data class RegistryError(val code: RegistryErrorCode, val message: String)

sealed interface RegistryResult<out T> {
    data class Success<T>(val value: T) : RegistryResult<T>
    data class Failure(val error: RegistryError) : RegistryResult<Nothing>
}
