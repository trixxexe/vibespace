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

    fun <R> map(transform: (T) -> R): VibeResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(message, throwable, code)
        is Loading -> Loading
    }

    fun onSuccess(action: (T) -> Unit): VibeResult<T> {
        if (this is Success) action(data)
        return this
    }

    fun onError(action: (Error) -> Unit): VibeResult<T> {
        if (this is Error) action(this)
        return this
    }
}

fun <T> VibeResult<T>.getOrDefault(defaultValue: T): T = when (this) {
    is VibeResult.Success -> data
    else -> defaultValue
}
