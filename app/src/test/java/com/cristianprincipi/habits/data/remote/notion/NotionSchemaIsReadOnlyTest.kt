package com.cristianprincipi.habits.data.remote.notion

import com.cristianprincipi.habits.core.network.RateLimiter
import com.cristianprincipi.habits.core.network.RetryPolicy
import com.cristianprincipi.habits.core.testing.FakeNotionTokenStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ⭐⭐ **M7.6's deliverable: the app cannot modify the user's Notion schema, and this fails if it
 * ever can.**
 *
 * ADR-010 makes a habit a **checkbox property** in the user's database, so creating, renaming or
 * deleting a habit is a *schema* change. The milestone's Definition of Done asks that the app
 * **provably** cannot make one — and M7.6 was written as *"verify by inspection"*, which is the
 * problem this file exists to fix. An inspection is true on the day it is written and silent for
 * ever afterwards; the next session to add a method to [NotionApi] would never hear from it.
 *
 * ### What "provably" rests on, in order
 *
 * 1. **`data/remote/notion` is the only package that can reach the network** (`CLAUDE.md` §6) —
 *    [nothingOutsideTheNotionPackageCanReachTheNetwork]. Without this the rest proves nothing, since
 *    a ViewModel with its own client could send anything.
 * 2. **[NotionApi] is that package's whole surface, and it has exactly six calls** —
 *    [theInterfaceHasExactlySixCallsAndASeventhHasToJustifyItselfHere].
 * 3. **Those six send exactly six requests, and none of them writes a schema** —
 *    [everyRequestTheClientCanSendIsOneOfTheseSix] and
 *    [nothingTheClientSendsCanCreateRenameRetypeOrDeleteAProperty].
 *
 * ### ⚠ Why the request DTOs are not enough on their own
 *
 * `CreatePageRequest` and `UpdatePageRequest` both carry `Map<String, JsonElement>` — arbitrary JSON,
 * which *looks* as though it could carry anything. It cannot carry a schema: those maps are property
 * **values** sent to a page endpoint, and a schema change is a write to the data source itself. That
 * is a claim about the **endpoint**, not about the payload type, which is why the assertions below
 * are about method and path rather than about bodies.
 *
 * Nothing here talks to the live API, ever (`CLAUDE.md` §6, §8).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotionSchemaIsReadOnlyTest {
    private val sent = mutableListOf<HttpRequestData>()

    /** The six calls, in the order [NotionApi] declares them. Every test below drives all of them. */
    private suspend fun NotionApi.everyCallThisAppCanMake() {
        searchDataSources()
        retrieveDatabase(A_DATABASE)
        retrieveDataSource(A_DATA_SOURCE)
        queryPages(A_DATA_SOURCE)
        createPage(A_DATA_SOURCE, mapOf(A_PROPERTY_ID to checkbox()))
        updatePage(A_PAGE, mapOf(A_PROPERTY_ID to checkbox()))
    }

    private fun TestScope.api(): KtorNotionApi {
        // Every response is an empty object. These tests assert on what was *sent*, so the bodies
        // fail to decode and every call returns Malformed — which is not retried, so the request
        // list stays one entry per call.
        val engine =
            MockEngine { request ->
                sent += request
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers =
                        Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        },
                )
            }
        return KtorNotionApi(
            httpClient = notionHttpClient(engine),
            tokenStore = FakeNotionTokenStore(FakeNotionTokenStore.NOT_A_REAL_TOKEN),
            rateLimiter = RateLimiter(nowMillis = { testScheduler.currentTime }),
            retryPolicy = RetryPolicy(),
        )
    }

    @Test
    fun everyRequestTheClientCanSendIsOneOfTheseSix() =
        runTest {
            api().everyCallThisAppCanMake()

            // The whole HTTP surface of this app, written out. A seventh line here is a decision,
            // which is the point: it cannot arrive as a side effect of adding a method.
            assertEquals(
                listOf(
                    "POST /v1/search",
                    "GET /v1/databases/$A_DATABASE",
                    "GET /v1/data_sources/$A_DATA_SOURCE",
                    "POST /v1/data_sources/$A_DATA_SOURCE/query",
                    "POST /v1/pages",
                    "PATCH /v1/pages/$A_PAGE",
                ),
                sent.map { "${it.method.value} ${it.url.encodedPath}" },
            )
        }

    @Test
    fun nothingTheClientSendsCanCreateRenameRetypeOrDeleteAProperty() =
        runTest {
            api().everyCallThisAppCanMake()

            // The rule, rather than the list: a schema lives on a database or a data source, so the
            // only safe thing to do to either is read it. `/query` is the exception that proves it —
            // a POST, because the filter travels in a body, but a read.
            val schemaWrites =
                sent.filter { request ->
                    val path = request.url.encodedPath
                    val schemaEndpoint =
                        (path.startsWith("/v1/databases/") || path.startsWith("/v1/data_sources/")) &&
                            !path.endsWith("/query")
                    schemaEndpoint && request.method != HttpMethod.Get
                }

            assertEquals(
                "Anything but GET on a database or a data source is a schema change, which ADR-010 " +
                    "leaves this app no code path for.",
                emptyList<String>(),
                schemaWrites.map { "${it.method.value} ${it.url.encodedPath}" },
            )
            // Nothing is ever removed or replaced wholesale, at any endpoint. ADR-036: v1 deletes
            // nothing, and NotionApi has no archive call either.
            assertEquals(
                emptyList<String>(),
                sent
                    .filter { it.method == HttpMethod.Delete || it.method == HttpMethod.Put }
                    .map { it.url.encodedPath },
            )
        }

    @Test
    fun theInterfaceHasExactlySixCallsAndASeventhHasToJustifyItselfHere() {
        // ⚠ The tripwire. The two tests above drive a hand-written list, so a *new* method would
        // slip past them unexercised — this is what notices. If you are here because you added a
        // call: it belongs in that list too, and if it writes a schema it does not belong at all.
        assertEquals(
            setOf(
                "searchDataSources",
                "retrieveDatabase",
                "retrieveDataSource",
                "queryPages",
                "createPage",
                "updatePage",
            ),
            NotionApi::class.java.declaredMethods
                // `searchDataSources` and `queryPages` have default argument values, and Kotlin
                // compiles a `…$default` bridge onto the interface for each. Dropping them by the
                // `$` cannot hide a real call: no method a person writes has one in its name.
                .filterNot { it.isSynthetic || '$' in it.name }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun nothingOutsideTheNotionPackageCanReachTheNetwork() {
        // `CLAUDE.md` §6: "data/remote/notion is the only package allowed to touch the network."
        // Everything else here is a proof about six methods, and it is worth nothing if a ViewModel
        // can build its own client — so this is the assumption the rest stands on, checked.
        val sources = File(repoRoot(), "app/src/main/java")
        assertTrue("Expected Kotlin sources at $sources", sources.isDirectory)

        val offenders =
            sources
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.invariantSeparatorsPath.contains(NOTION_PACKAGE_PATH) }
                .filter { file -> NETWORKING_IMPORTS.any { it in file.readText() } }
                .map { it.name }
                .toList()

        assertEquals(
            "These files build network calls outside data/remote/notion, so nothing above can still " +
                "claim the app's requests are the six in NotionApi.",
            emptyList<String>(),
            offenders,
        )
    }

    /** Walks up from the module directory, so this works whichever of the two Gradle runs it from. */
    private fun repoRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null && !File(candidate, "settings.gradle.kts").isFile) {
            candidate = candidate.parentFile
        }
        return requireNotNull(candidate) { "No settings.gradle.kts above ${System.getProperty("user.dir")}" }
    }

    private fun checkbox() = JsonObject(mapOf("checkbox" to JsonPrimitive(true)))

    private companion object {
        const val A_DATABASE = "8f2c41d97b5e40a3ac6e1d9f0b73e254"
        const val A_DATA_SOURCE = "0b1d3f7a95c84e62b8a0f4d2e6c15938"
        const val A_PAGE = "page-1"

        /** Percent-encoded, exactly as Notion sends property ids (`docs/notion-api.md` §8). */
        const val A_PROPERTY_ID = "ZI%40W"

        const val NOTION_PACKAGE_PATH = "data/remote/notion/"

        val NETWORKING_IMPORTS =
            listOf("io.ktor.", "okhttp3.", "java.net.HttpURLConnection", "java.net.URLConnection")
    }
}
