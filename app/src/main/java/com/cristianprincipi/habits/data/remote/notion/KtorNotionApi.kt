package com.cristianprincipi.habits.data.remote.notion

import com.cristianprincipi.habits.core.network.RateLimiter
import com.cristianprincipi.habits.core.network.RetryPolicy
import com.cristianprincipi.habits.data.remote.notion.auth.NotionToken
import com.cristianprincipi.habits.data.remote.notion.auth.NotionTokenStore
import com.cristianprincipi.habits.data.remote.notion.dto.CreatePageRequest
import com.cristianprincipi.habits.data.remote.notion.dto.DataSourceParent
import com.cristianprincipi.habits.data.remote.notion.dto.LastEditedTimeFilter
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDataSourceDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDatabaseDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionErrorDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionListDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPageDto
import com.cristianprincipi.habits.data.remote.notion.dto.QueryDataSourceRequest
import com.cristianprincipi.habits.data.remote.notion.dto.SearchRequest
import com.cristianprincipi.habits.data.remote.notion.dto.TimestampCondition
import com.cristianprincipi.habits.data.remote.notion.dto.UpdatePageRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [NotionApi] over Ktor. **The only class in the app that makes an HTTP request.**
 *
 * Everything interesting is in [exchange], and it is interesting for four reasons, all of them rules
 * from `CLAUDE.md` §3.5 and §3.6 turned into code that cannot be bypassed:
 *
 * 1. **The token is read per request and never held.** Not a constructor field, not a
 *    `defaultRequest` header. Disconnect (`NotionTokenStore.clear`) therefore takes effect on the
 *    very next call, with no client to rebuild and no cached copy to miss.
 * 2. **Every request passes through the shared [RateLimiter]** — one instance for the process, which
 *    is what makes "never call the API in a bare loop" true by construction rather than by review.
 * 3. **[RetryPolicy] decides every retry**, including honouring `Retry-After` on a 429. No status
 *    code is interpreted twice.
 * 4. **Nothing throws.** Failures are [NotionError] values, because a failed sync is a state to
 *    render, not an exception to catch somewhere and forget.
 */
@Singleton
class KtorNotionApi
    @Inject
    constructor(
        private val httpClient: HttpClient,
        private val tokenStore: NotionTokenStore,
        private val rateLimiter: RateLimiter,
        private val retryPolicy: RetryPolicy,
    ) : NotionApi {
        override suspend fun searchDataSources(
            query: String?,
            startCursor: String?,
        ): NotionResult<NotionListDto<NotionDataSourceDto>> =
            exchange(
                method = HttpMethod.Post,
                path = "search",
                body = NotionJson.encodeToString(SearchRequest(query = query, startCursor = startCursor)),
            ).decodeAs(NotionListDto.serializer(NotionDataSourceDto.serializer()))

        override suspend fun retrieveDatabase(databaseId: String): NotionResult<NotionDatabaseDto> =
            exchange(HttpMethod.Get, "databases/$databaseId").decodeAs(NotionDatabaseDto.serializer())

        override suspend fun retrieveDataSource(dataSourceId: String): NotionResult<NotionDataSourceDto> =
            exchange(HttpMethod.Get, "data_sources/$dataSourceId").decodeAs(NotionDataSourceDto.serializer())

        override suspend fun queryPages(
            dataSourceId: String,
            editedAtOrAfter: Instant?,
            startCursor: String?,
        ): NotionResult<NotionListDto<NotionPageDto>> {
            val request =
                QueryDataSourceRequest(
                    // Instant.toString() is ISO-8601 in UTC with a trailing `Z`, so the offset is
                    // always explicit. docs/notion-api.md §7 warns that a missing one silently
                    // defaults to UTC — right today, and exactly the kind of default that moves a
                    // pull window by hours if it ever changes.
                    filter =
                        editedAtOrAfter?.let {
                            LastEditedTimeFilter(
                                TimestampCondition(onOrAfter = it.toString()),
                            )
                        },
                    startCursor = startCursor,
                )
            return exchange(
                method = HttpMethod.Post,
                path = "data_sources/$dataSourceId/query",
                body = NotionJson.encodeToString(request),
            ).decodeAs(NotionListDto.serializer(NotionPageDto.serializer()))
        }

        override suspend fun createPage(
            dataSourceId: String,
            properties: Map<String, JsonElement>,
        ): NotionResult<NotionPageDto> =
            exchange(
                method = HttpMethod.Post,
                path = "pages",
                body =
                    NotionJson.encodeToString(
                        CreatePageRequest(DataSourceParent(dataSourceId = dataSourceId), properties),
                    ),
            ).decodeAs(NotionPageDto.serializer())

        override suspend fun updatePage(
            pageId: String,
            properties: Map<String, JsonElement>,
        ): NotionResult<NotionPageDto> =
            exchange(
                method = HttpMethod.Patch,
                path = "pages/$pageId",
                body = NotionJson.encodeToString(UpdatePageRequest(properties)),
            ).decodeAs(NotionPageDto.serializer())

        /**
         * One call, retried until [RetryPolicy] says stop.
         *
         * Reads the token first and gives up immediately if there is none — a request without one is
         * a guaranteed 401 that spends a round trip and a slot in the rate limit to learn nothing.
         */
        private suspend fun exchange(
            method: HttpMethod,
            path: String,
            body: String? = null,
        ): NotionResult<HttpResponse> {
            val token = tokenStore.read() ?: return NotionResult.Failure(NotionError.NotConnected)
            var attempt = 1
            while (true) {
                when (val step = attemptOnce(attempt, method, path, body, token)) {
                    is Step.Done -> return step.result
                    is Step.Retry -> delay(step.after)
                }
                attempt++
            }
        }

        // ReturnCount: the three exits are the three outcomes of one attempt — no response, a good
        // response, a bad one — and folding them into one expression would hide that.
        // TooGenericExceptionCaught: Ktor reports "the request never happened" as at least three
        // unrelated families — IOException from the socket, HttpRequestTimeoutException from the
        // timeout plugin, and UnresolvedAddressException, which is a RuntimeException and is what an
        // offline device actually throws. Naming a subset is how the offline case escapes as a crash.
        @Suppress("ReturnCount", "TooGenericExceptionCaught")
        private suspend fun attemptOnce(
            attempt: Int,
            method: HttpMethod,
            path: String,
            body: String?,
            token: NotionToken,
        ): Step {
            val response =
                try {
                    rateLimiter.withPermit { send(method, path, body, token) }
                } catch (cancellation: CancellationException) {
                    // Rethrown, never retried: a cancelled sync is a decision, not a network blip.
                    throw cancellation
                } catch (failure: Exception) {
                    return stepAfter(attempt, status = null, retryAfter = null) {
                        NotionResult.Failure(NotionError.Network(failure))
                    }
                }

            if (response.status.isSuccess()) return Step.Done(NotionResult.Success(response))

            val retryAfter = response.retryAfter()
            return stepAfter(attempt, response.status.value, retryAfter) {
                NotionResult.Failure(response.toNotionError(retryAfter))
            }
        }

        /** Asks [RetryPolicy] whether to go round again, and builds the failure if not. */
        private suspend fun stepAfter(
            attempt: Int,
            status: Int?,
            retryAfter: Duration?,
            giveUp: suspend () -> NotionResult<HttpResponse>,
        ): Step {
            val after = retryPolicy.delayBefore(attempt, status, retryAfter)
            return if (after == null) Step.Done(giveUp()) else Step.Retry(after)
        }

        private suspend fun send(
            method: HttpMethod,
            path: String,
            body: String?,
            token: NotionToken,
        ): HttpResponse =
            httpClient.request(path) {
                this.method = method
                // The one place the token is ever written onto a request. CLAUDE.md §3.6: it must
                // not reach a log from here, which is why no Logging plugin exists — see
                // notionHttpClient.
                header(HttpHeaders.Authorization, "Bearer ${token.value}")
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
    }

/** One attempt's verdict: finished with a result, or worth another try after a wait. */
private sealed interface Step {
    data class Done(
        val result: NotionResult<HttpResponse>,
    ) : Step

    data class Retry(
        val after: Duration,
    ) : Step
}

/**
 * `docs/notion-api.md` §6: `Retry-After` is "an integer number of seconds".
 *
 * Anything unparseable is treated as absent rather than as zero. A zero would tell [RetryPolicy] to
 * hammer the endpoint that has just asked us to stop, which is the one behaviour a 429 forbids.
 */
private fun HttpResponse.retryAfter(): Duration? = headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.seconds

/**
 * Turns a non-2xx into the closed set of [NotionError]s.
 *
 * The status line is the authority; the body only supplies detail. That ordering matters most for
 * `404`, where Notion's own message says "Could not find database" and the truth is "…or you never
 * shared it with the connection" (`docs/notion-api.md` §3.1).
 */
private suspend fun HttpResponse.toNotionError(retryAfter: Duration?): NotionError {
    val detail = errorDetail()
    return when (status.value) {
        UNAUTHORIZED -> NotionError.Unauthorized
        FORBIDDEN -> NotionError.Forbidden
        NOT_FOUND -> NotionError.MissingOrNotShared
        TOO_MANY_REQUESTS -> NotionError.RateLimited(retryAfter)
        in SERVER_ERRORS -> NotionError.Server(status.value)
        else -> NotionError.Invalid(code = detail?.code.orEmpty(), message = detail?.message.orEmpty())
    }
}

/**
 * The error body, or `null` if there was not one worth reading.
 *
 * Decoded from text rather than through content negotiation on purpose: a proxy, a captive portal or
 * a gateway answers with HTML, and failing to parse *that* must not become a second failure on top
 * of the first. The status code alone already builds every [NotionError] above.
 */
private suspend fun HttpResponse.errorDetail(): NotionErrorDto? {
    @Suppress("SwallowedException") // The status code is the answer; the body was only ever a bonus.
    val text =
        try {
            bodyAsText()
        } catch (unreadable: IOException) {
            return null
        }
    return runCatching { NotionJson.decodeFromString<NotionErrorDto>(text) }.getOrNull()
}

/**
 * Deserialises a successful response, or reports [NotionError.Malformed].
 *
 * **The serializer is passed in rather than looked up from a reified type — ADR-043.** Ktor's
 * `body<T>()` resolves the serializer by reflection at runtime; a compile-time serializer resolves
 * it at build time. Both were tried here, on the same bug: a `@Serializable` class whose companion
 * had been declared `private`, which makes the generated `serializer()` private with it. Through
 * reflection that surfaced as a `NotionError.Malformed` carrying an `IllegalAccessException` —
 * a runtime failure, in the middle of a sync, that reads like Notion changed its payload.
 * Through an explicit serializer it was a compile error naming the class. The client is built the
 * second way, and touches no reflection as a result.
 *
 * Malformed is its own case rather than a crash: `ignoreUnknownKeys` means a *new* field can never
 * cause it, so reaching it means a field we depend on changed shape or disappeared. That is a
 * version-pin problem (`docs/notion-api.md` §2.1), and it should read like one.
 */
private suspend fun <T> NotionResult<HttpResponse>.decodeAs(deserializer: DeserializationStrategy<T>): NotionResult<T> =
    when (this) {
        is NotionResult.Failure -> {
            this
        }

        is NotionResult.Success -> {
            try {
                NotionResult.Success(NotionJson.decodeFromString(deserializer, value.bodyAsText()))
            } catch (unparseable: SerializationException) {
                NotionResult.Failure(NotionError.Malformed(unparseable))
            } catch (unreadable: IOException) {
                // The body died mid-stream. Neither catch can swallow a CancellationException,
                // which is neither of these — a cancelled sync still stops rather than being
                // reported as a bad payload.
                NotionResult.Failure(NotionError.Malformed(unreadable))
            }
        }
    }

private const val UNAUTHORIZED = 401
private const val FORBIDDEN = 403
private const val NOT_FOUND = 404
private const val TOO_MANY_REQUESTS = 429
private const val FIRST_SERVER_ERROR = 500

/** Notion's own `529 service_overload` sits inside this range, so it needs no separate case. */
private const val LAST_SERVER_ERROR = 599
private val SERVER_ERRORS = FIRST_SERVER_ERROR..LAST_SERVER_ERROR
