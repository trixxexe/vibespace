package com.vibespace.core.common.result

/**
 * A sealed hierarchy representing the outcome of an operation.
 * Used across repositories, services, and view models.
 */
sealed interface VibeResult<out T> {

    data class Success<out T>(val data: T) : VibeResult<T>

    data class Error(
        val message: String,
        val throwable: Throwable? = null,
        val code: Int = -1
    ) : VibeResult<Nothing>

    data object Loading : VibeResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isError: Boolean
        get() = this is Error

    val isLoading: Boolean
        get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun getOrDefault(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> defaultValue
    }
}

inline fun <T, R> VibeResult<T>.map(transform: (T) -> R): VibeResult<R> = when (this) {
    is VibeResult.Success -> VibeResult.Success(transform(data))
    is VibeResult.Error -> VibeResult.Error(message, throwable, code)
    is VibeResult.Loading -> VibeResult.Loading
}

inline fun <T> VibeResult<T>.onSuccess(action: (T) -> Unit): VibeResult<T> {
    if (this is VibeResult.Success) action(data)
    return this
}

inline fun <T> VibeResult<T>.onError(action: (VibeResult.Error) -> Unit): VibeResult<T> {
    if (this is VibeResult.Error) action(this)
    return this
}
