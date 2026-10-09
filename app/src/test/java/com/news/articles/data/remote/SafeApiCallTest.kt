package com.news.articles.data.remote

import com.news.articles.domain.model.AppError
import com.news.articles.domain.model.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SafeApiCallTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun httpException(status: Int, apiCode: String? = null): HttpException {
        val body = apiCode
            ?.let { """{"status":"error","code":"$it","message":"message"}""" }
            .orEmpty()
            .toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(status, body))
    }

    private suspend fun failureOf(error: Throwable): AppError =
        (safeApiCall<Unit>(json) { throw error } as AppResult.Failure).error

    @Test
    fun `returns the value when the call succeeds`() = runTest {
        val result = safeApiCall(json) { 42 }

        assertEquals(AppResult.Success(42), result)
    }

    @Test
    fun `maps a socket timeout to timeout`() = runTest {
        assertEquals(AppError.Timeout, failureOf(SocketTimeoutException()))
    }

    @Test
    fun `maps connection problems to no internet`() = runTest {
        assertEquals(AppError.NoInternet, failureOf(UnknownHostException()))
        assertEquals(AppError.NoInternet, failureOf(IOException()))
    }

    @Test
    fun `maps an invalid key response to unauthorized`() = runTest {
        assertEquals(AppError.Unauthorized, failureOf(httpException(401, "apiKeyInvalid")))
        assertEquals(AppError.Unauthorized, failureOf(httpException(401)))
    }

    @Test
    fun `maps a known api code even when the status is unexpected`() = runTest {
        assertEquals(AppError.Unauthorized, failureOf(httpException(400, "apiKeyMissing")))
        assertEquals(AppError.RateLimited, failureOf(httpException(400, "rateLimited")))
    }

    @Test
    fun `maps too many requests to rate limited`() = runTest {
        assertEquals(AppError.RateLimited, failureOf(httpException(429, "rateLimited")))
        assertEquals(AppError.RateLimited, failureOf(httpException(429)))
    }

    @Test
    fun `maps server failures to server`() = runTest {
        assertEquals(AppError.Server, failureOf(httpException(500)))
        assertEquals(AppError.Server, failureOf(httpException(503)))
    }

    @Test
    fun `maps other http failures to unknown`() = runTest {
        assertEquals(AppError.Unknown, failureOf(httpException(400, "parametersMissing")))
        assertEquals(AppError.Unknown, failureOf(httpException(404)))
    }

    @Test
    fun `maps an unreadable response to unknown`() = runTest {
        assertEquals(AppError.Unknown, failureOf(SerializationException("bad json")))
        assertEquals(AppError.Unknown, failureOf(IllegalStateException()))
    }

    @Test
    fun `rethrows cancellation instead of reporting an error`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                safeApiCall<Unit>(json) { throw CancellationException("cancelled") }
            }
        }
    }
}
