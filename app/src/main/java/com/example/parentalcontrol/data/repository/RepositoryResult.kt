package com.example.parentalcontrol.data.repository

sealed class RepositoryResult<out T> {
    data class Success<T>(val data: T) : RepositoryResult<T>()
    data class Error(val message: String, val code: Int = -1, val exception: Throwable? = null) : RepositoryResult<Nothing>()
}

data class NetworkError(
    val userMessage: String,
    val technicalMessage: String,
    val exception: Throwable? = null
)
