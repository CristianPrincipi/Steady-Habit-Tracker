package com.cristianprincipi.habits.core.testing

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the Ktor toolchain actually works, not merely that it resolves.
 *
 * The same reasoning as `RoomToolchainProbe` and `TestToolchainTest`: a dependency that appears in
 * the catalog and compiles has been proved to *download*. This is what proves the four modules
 * agree with each other, that the OkHttp engine loads on this JVM, and that the JSON config
 * `CLAUDE.md` §6 mandates behaves the way the Notion client will need it to.
 *
 * **No network.** `MockEngine` is the transport throughout — `CLAUDE.md` §6: never point an
 * automated test at the live API. It is also the foundation M4.14's fake Notion API is built on,
 * so this doubles as the first proof that approach works.
 *
 * Delete none of this when M4.5 lands. When a real client stops working, the first question is
 * whether the plumbing or the code broke, and that is exactly what these answer.
 */
class KtorToolchainTest {
    /** Deliberately missing `last_edited_time`, which the fixture below sends. See the test. */
    @Serializable
    private data class Page(
        val id: String,
        @SerialName("object") val objectType: String,
    )

    private val json =
        Json {
            // CLAUDE.md §6: "Notion adds fields; an unknown key must never break a sync."
            ignoreUnknownKeys = true
        }

    private fun clientReturning(
        payload: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = HttpClient(
        MockEngine {
            respond(
                payload,
                status,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        },
    ) {
        install(ContentNegotiation) { json(json) }
    }

    @Test
    fun theOkHttpEngineLoads() {
        // The engine is looked up by service loader at construction. If the artifact were missing
        // or mismatched, this line throws rather than failing later at the first request.
        HttpClient(OkHttp).use { client ->
            val engine = client.engine::class.qualifiedName.orEmpty()
            assertTrue("Expected an OkHttp engine, got $engine", engine.contains("okhttp", ignoreCase = true))
        }
    }

    @Test
    fun contentNegotiationDeserialisesAJsonBody() =
        runTest {
            val client = clientReturning("""{"object":"page","id":"abc-123"}""")

            val page: Page = client.get("https://example.invalid/v1/pages/abc-123").body()

            assertEquals("abc-123", page.id)
            assertEquals("page", page.objectType)
        }

    @Test
    fun anUnknownKeyDoesNotBreakDeserialisation() =
        runTest {
            // The single most important line of the JSON config. Notion adds fields to its
            // responses without a version bump; without ignoreUnknownKeys the first one it adds
            // would fail every sync at once, and the cause would look like a network problem.
            val withExtras =
                """{"object":"page","id":"abc-123","last_edited_time":"2026-08-22T09:00:00.000Z",
                   "archived":false,"properties":{"Date":{"id":"M%3BBw","type":"date"}}}"""
            val client = clientReturning(withExtras)

            val page: Page = client.get("https://example.invalid/v1/pages/abc-123").body()

            assertEquals("abc-123", page.id)
        }

    @Test
    fun aNonSuccessStatusIsReadableRatherThanThrown() =
        runTest {
            // Ktor does not throw on 4xx unless HttpCallValidator is installed. The Notion client
            // depends on that: `429` carries a Retry-After header and `404` means "missing OR not
            // shared" (docs/notion-api.md §3.1, §6) — both are values to inspect, not exceptions.
            val client =
                clientReturning(
                    """{"object":"error","status":429,"code":"rate_limited"}""",
                    HttpStatusCode.TooManyRequests,
                )

            val response: HttpResponse = client.get("https://example.invalid/v1/data_sources/x/query")

            assertEquals(HttpStatusCode.TooManyRequests, response.status)
        }
}
