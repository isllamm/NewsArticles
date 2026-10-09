package com.news.articles.data.remote

import com.news.articles.data.remote.dto.ErrorResponseDto
import com.news.articles.domain.model.AppError
import com.news.articles.domain.model.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500

private val AUTH_ERROR_CODES = setOf("apiKeyInvalid", "apiKeyMissing", "apiKeyDisabled")
private val RATE_LIMIT_ERROR_CODES = setOf("rateLimited", "apiKeyExhausted")

suspend fun <T> safeApiCall(json: Json, block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        AppResult.Failure(exception.toAppError(json))
    }

fun Throwable.toAppError(json: Json): AppError = when (this) {
    is SocketTimeoutException -> AppError.Timeout
    is IOException -> AppError.NoInternet
    is HttpException -> toHttpAppError(json)
    else -> AppError.Unknown
}

private fun HttpException.toHttpAppError(json: Json): AppError {
    val apiCode = response()?.errorBody()?.string()?.let { body ->
        runCatching { json.decodeFromString<ErrorResponseDto>(body).code }.getOrNull()
    }
    val statusCode = code()
    return when {
        statusCode == HTTP_UNAUTHORIZED || apiCode in AUTH_ERROR_CODES -> AppError.Unauthorized
        statusCode == HTTP_TOO_MANY_REQUESTS || apiCode in RATE_LIMIT_ERROR_CODES -> AppError.RateLimited
        statusCode >= HTTP_SERVER_ERROR -> AppError.Server
        else -> AppError.Unknown
    }
}
