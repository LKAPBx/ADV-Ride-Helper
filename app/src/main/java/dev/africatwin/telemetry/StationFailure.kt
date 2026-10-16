package dev.africatwin.telemetry

internal class StationResponseFailure(val reason: String, val busy: Boolean = false, val retryAfterMillis: Long = 0) : java.io.IOException(reason)
internal object StationFailure {
    fun shouldRetry(error: Throwable, retries: Int) = retries < 5 && when (error) {
        is StationResponseFailure -> error.busy
        is java.net.SocketTimeoutException, is java.net.ConnectException, is java.net.SocketException -> true
        else -> false
    }
    fun retryDelayMillis(error: Throwable, retries: Int): Long {
        val backoff = listOf(5_000L, 10_000L, 20_000L, 30_000L, 45_000L)[retries.coerceIn(1, 5) - 1]
        return maxOf(backoff, (error as? StationResponseFailure)?.retryAfterMillis ?: 0)
    }
    fun describe(error: Throwable): String = when (error) {
        is StationResponseFailure -> error.reason
        is java.net.SocketTimeoutException -> "Station service timed out; retry when connection improves"
        is java.net.UnknownHostException -> "Cannot reach station service; check internet connection"
        is javax.net.ssl.SSLException -> "Secure connection failed; check network and phone date/time"
        is java.net.ConnectException -> "Could not connect to station service"
        is java.io.IOException -> "Network connection interrupted during station search"
        is SecurityException -> "Station search access blocked; check app permissions"
        else -> if (error.javaClass.simpleName == "JSONException") "Station service returned an invalid response" else "Could not process station results; see Advanced report"
    }
    fun http(code: Int): String = when (code) {
        429 -> "Station service is limiting requests; try again later"
        502, 503, 504 -> "Station service is busy/unavailable (HTTP $code); try later"
        400 -> "Station service rejected the search (HTTP 400)"
        401, 403, 406 -> "Station service denied the request (HTTP $code)"
        in 300..399 -> "Station service redirected the request (HTTP $code)"
        else -> "Station service returned HTTP $code"
    }
}
