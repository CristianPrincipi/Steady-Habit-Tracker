package com.cristianprincipi.habits.data.remote.notion

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.url
import kotlin.time.Duration.Companion.seconds

/**
 * Builds the Ktor client every Notion call goes through.
 *
 * **A function taking an engine, rather than a Hilt `@Provides` that hardcodes OkHttp**, for one
 * reason: the tests build the client this way too, with a `MockEngine` in place of the network. A
 * test that constructed its own differently-configured client would prove things about a client
 * that does not ship — it would not pin the version header, would not ignore unknown keys, and
 * would not catch the day somebody changes one of those here.
 *
 * **There is no `Logging` plugin, and its absence is enforced by the build.** `CLAUDE.md` §3.6
 * requires the `Authorization` header redacted *before* logging is switched on at any level, so
 * `gradle/libs.versions.toml` deliberately omits `ktor-client-logging` — an `install(Logging)` here
 * does not compile today. Whoever adds that artifact takes on the whole of §3.6 with it, starting
 * with `sanitizeHeader { it == HttpHeaders.Authorization }`. Until then the compiler is the guard,
 * which is stronger than a comment.
 *
 * **There is no `ContentNegotiation` plugin either, and that one is a decision — ADR-043.** Bodies
 * are serialised and parsed explicitly against [NotionJson] with compile-time serializers, so a
 * missing or inaccessible serializer is a build failure rather than a `Malformed` response in the
 * middle of a sync. Nothing is left for the plugin to do, and installing a plugin the client does
 * not use would be one more piece of configuration nothing tests.
 */
fun notionHttpClient(engine: HttpClientEngine): HttpClient =
    HttpClient(engine) {
        // Ktor's default, set explicitly because the client DEPENDS on it. A 429 carries the
        // Retry-After this app honours and a 404 means "missing or not shared" (notion-api.md §3.1,
        // §6) — both are values to read off the response, and expectSuccess = true would turn them
        // into exceptions thrown from inside the retry loop instead.
        expectSuccess = false

        // Without a timeout a half-open socket parks a sync coroutine indefinitely, and WorkManager
        // eventually kills the worker with no error anyone can read. These bound it instead, and a
        // timeout arrives as a retryable "no response" — which is what it is.
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds
            connectTimeoutMillis = CONNECT_TIMEOUT.inWholeMilliseconds
            socketTimeoutMillis = SOCKET_TIMEOUT.inWholeMilliseconds
        }

        defaultRequest {
            url(NotionApiSpec.BASE_URL)
            // Pinned here rather than at each call site so that no request can be written that
            // omits it — which the API answers with `400 missing_version` (notion-api.md §2).
            header(NotionApiSpec.VERSION_HEADER, NotionApiSpec.VERSION)
        }
    }

/**
 * Long enough for a 100-page query on a slow connection, short enough that a stuck request fails
 * within one sync window rather than holding the worker open. Not a number from the Notion docs —
 * they publish no request-duration guidance — so it is ours, and deliberately generous.
 */
private val REQUEST_TIMEOUT = 30.seconds

private val CONNECT_TIMEOUT = 15.seconds

private val SOCKET_TIMEOUT = 30.seconds
