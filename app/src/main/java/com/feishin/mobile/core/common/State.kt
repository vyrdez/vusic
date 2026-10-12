package com.feishin.mobile.core.common

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Data<T>(val value: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface Result<out T> {
    data class Ok<T>(val value: T) : Result<T>
    data class Err(val message: String, val cause: Throwable? = null) : Result<Nothing>
}
