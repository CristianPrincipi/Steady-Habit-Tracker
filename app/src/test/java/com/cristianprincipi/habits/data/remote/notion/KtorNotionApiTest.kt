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
import io.ktor.http.content.TextContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * The Notion client, against a faked transport.
 *
 * **`CLAUDE.md` §6 and §8: never point an automated test at the live API.** Every request below is
 * answered by Ktor's `MockEngine`, which is also what M4.14's fake Notion API will be built on.
 *
 * The client under test is built with [notionHttpClient] — the same function the Hilt module calls —
 * so these tests exercise the shipped configuration: the pinned version header, `ignoreUnknownKeys`,
 * `expectSuccess = false`, the timeouts. A test that assembled its own client would prove things
 * about a client that does not ship.
 *
 * `RateLimiter` is driven from the scheduler's virtual clock for the same reason `RateLimiterTest`
 * does it: the 334ms spacing is real, and a test that waited it out for every request would take
 * longer than the whole suite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KtorNotionApiTest {
    private val sent = mutableListOf<HttpRequestData>()

    // A property id, percent-encoded exactly as Notion sends them (docs/notion-api.md §8). The
    // mapping is keyed on ids like this one, and writes use them as keys (ADR-038).
    private val readPropertyId = "ZI%40W"
    private val datePropertyId = "M%3BBw"

    private data class Canned(
        val body: String = "{}",
        val status: HttpStatusCode = HttpStatusCode.OK,
        val retryAfterSeconds: Int? = null,
        val failWith: IOException? = null,
    )

    private fun TestScope.api(
        vararg canned: Canned,
        token: Boolean = true,
        retryPolicy: RetryPolicy = RetryPolicy(),
    ): KtorNotionApi {
        val engine =
            MockEngine { request ->
                sent += request
                val next = canned.getOrElse(sent.size - 1) { canned.lastOrNull() ?: Canned() }
                next.failWith?.let { throw it }
                respond(
                    content = next.body,
                    status = next.status,
                    headers =
                        Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            next.retryAfterSeconds?.let { append(HttpHeaders.RetryAfter, it.toString()) }
                        },
                )
            }
        return KtorNotionApi(
            httpClient = notionHttpClient(engine),
            tokenStore = FakeNotionTokenStore(if (token) FakeNotionTokenStore.NOT_A_REAL_TOKEN else null),
            rateLimiter = RateLimiter(nowMillis = { testScheduler.currentTime }),
            retryPolicy = retryPolicy,
        )
    }

    private fun HttpRequestData.bodyText(): String = (body as TextContent).text

    private val emptyListBody = """{"object":"list","results":[],"has_more":false,"next_cursor":null}"""

    private val onePage =
        """
        {
          "object": "page",
          "id": "page-1",
          "created_time": "2026-08-19T08:00:00.000Z",
          "last_edited_time": "2026-08-22T09:00:00.000Z",
          "in_trash": false,
          "is_archived": false,
          "properties": {
            "📖 Read": { "id": "$readPropertyId", "type": "checkbox", "checkbox": true }
          }
        }
        """.trimIndent()

    @Test
    fun everyRequestPinsTheVersionAndCarriesTheBearerToken() =
        runTest {
            api(Canned(emptyListBody)).searchDataSources()

            val request = sent.single()
            assertEquals("api.notion.com", request.url.host)
            // docs/notion-api.md §2: omitting this is a 400 missing_version, not a silent default.
            assertEquals(NotionApiSpec.VERSION, request.headers[NotionApiSpec.VERSION_HEADER])
            assertEquals(
                "Bearer ${FakeNotionTokenStore.NOT_A_REAL_TOKEN.value}",
                request.headers[HttpHeaders.Authorization],
            )
        }

    @Test
    fun theSchemaIsReadFromTheDataSourceEndpoint() =
        runTest {
            // ADR-037: the properties — which in this database ARE the habits — hang off a data
            // source, not the database. A client that asked /v1/databases/{id} for a schema would
            // get an object with no properties on it and no error to explain why.
            api(Canned("""{"object":"data_source","id":"ds-1","title":[],"properties":{}}"""))
                .retrieveDataSource("ds-1")

            assertEquals("/v1/data_sources/ds-1", sent.single().url.encodedPath)
            assertEquals(HttpMethod.Get, sent.single().method)
        }

    @Test
    fun aDatabaseIsOnlyEverAskedForItsDataSources() =
        runTest {
            val body =
                """{"object":"database","id":"db-1","title":[{"plain_text":"Habits"}],
                   "data_sources":[{"id":"ds-1","name":"Habits data source"}]}"""

            val result = api(Canned(body)).retrieveDatabase("db-1")

            assertEquals("/v1/databases/db-1", sent.single().url.encodedPath)
            val database = result.getOrNull()!!
            assertEquals("Habits", database.displayTitle)
            assertEquals(listOf("ds-1"), database.dataSources.map { it.id })
        }

    @Test
    fun searchAsksForDataSourcesRatherThanPages() =
        runTest {
            api(Canned(emptyListBody)).searchDataSources()

            val request = sent.single()
            assertEquals("/v1/search", request.url.encodedPath)
            val filter = NotionJson.parseToJsonElement(request.bodyText()).jsonObject["filter"]!!.jsonObject
            assertEquals("object", filter["property"]!!.jsonPrimitive.content)
            assertEquals("data_source", filter["value"]!!.jsonPrimitive.content)
            // encodeDefaults = true is what puts those two on the wire at all; at kotlinx's default
            // they are Kotlin defaults and would be omitted, turning this into an unfiltered search.
        }

    @Test
    fun aFirstRunQuerySendsNoFilterAndAsksForTheLargestPage() =
        runTest {
            api(Canned(emptyListBody)).queryPages(dataSourceId = "ds-1")

            val request = sent.single()
            assertEquals("/v1/data_sources/ds-1/query", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            val body = NotionJson.parseToJsonElement(request.bodyText()).jsonObject
            assertNull(body["filter"])
            assertEquals(NotionApiSpec.MAX_PAGE_SIZE, body["page_size"]!!.jsonPrimitive.content.toInt())
        }

    @Test
    fun anIncrementalQueryFiltersOnLastEditedTimeWithAnExplicitOffset() =
        runTest {
            // CLAUDE.md §3.5: "pull incrementally, filtered on last-edited time since the last
            // successful sync. Never re-fetch the entire database on every run."
            api(Canned(emptyListBody)).queryPages(
                dataSourceId = "ds-1",
                editedAtOrAfter = Instant.parse("2026-08-22T09:00:00Z"),
            )

            val filter = NotionJson.parseToJsonElement(sent.single().bodyText()).jsonObject["filter"]!!.jsonObject
            assertEquals("last_edited_time", filter["timestamp"]!!.jsonPrimitive.content)
            val after = filter["last_edited_time"]!!.jsonObject["on_or_after"]!!.jsonPrimitive.content
            // docs/notion-api.md §7: "if no timezone is provided, then the timezone defaults to UTC".
            // Relying on that default is how a pull window silently moves by hours.
            assertTrue("Expected an explicit offset in $after", after.endsWith("Z"))
            assertEquals("2026-08-22T09:00:00Z", after)
        }

    @Test
    fun everyQueryAsksForAscendingLastEditedOrder() =
        runTest {
            // ⭐ Not a nicety. The pull's resume point is MAX(remoteLastEditedAt) over the days
            // already stored, and docs/notion-api.md §14 records that Notion "doesn't guarantee any
            // particular sort order when no sort parameters are provided". Unsorted, a run killed
            // halfway through pagination advances that watermark past pages it never applied, and
            // the next run — asking for everything edited *after* it — never sees them again.
            api(Canned(emptyListBody)).queryPages(dataSourceId = "ds-1")

            val sorts = NotionJson.parseToJsonElement(sent.single().bodyText()).jsonObject["sorts"]!!.jsonArray
            val sort = sorts.single().jsonObject
            // A TIMESTAMP sort, not a property sort: a property sort would name a column in the
            // user's schema, which CLAUDE.md §3.4 forbids depending on.
            assertEquals("last_edited_time", sort["timestamp"]!!.jsonPrimitive.content)
            assertEquals("ascending", sort["direction"]!!.jsonPrimitive.content)
        }

    @Test
    fun aCursorIsForwardedAsStartCursor() =
        runTest {
            api(Canned(emptyListBody)).queryPages(dataSourceId = "ds-1", startCursor = "cursor-2")

            val body = NotionJson.parseToJsonElement(sent.single().bodyText()).jsonObject
            assertEquals("cursor-2", body["start_cursor"]!!.jsonPrimitive.content)
        }

    @Test
    fun aPageIsReadBackWithItsPropertiesLeftAsRawJson() =
        runTest {
            val list = """{"object":"list","results":[$onePage],"request_status":{"type":"complete"}}"""

            val page =
                api(Canned(list))
                    .queryPages("ds-1")
                    .getOrNull()!!
                    .results
                    .single()

            assertEquals("page-1", page.id)
            assertEquals("2026-08-22T09:00:00.000Z", page.lastEditedTime)
            assertFalse(page.isGone)
            // Keyed by NAME on the way in — the id is inside the value. That asymmetry is why the
            // sync engine matches on the inner id and never on this map key (CLAUDE.md §3.4).
            val property = page.properties.values.single()
            assertEquals(readPropertyId, property["id"]!!.jsonPrimitive.content)
            assertTrue(property["checkbox"]!!.jsonPrimitive.boolean)
        }

    @Test
    fun anArchivedOrTrashedPageReportsItselfGone() =
        runTest {
            val trashed =
                """{"object":"list","results":[{"id":"p","last_edited_time":"2026-08-22T09:00:00.000Z",
                   "in_trash":true,"is_archived":false}],"has_more":false}"""

            val page =
                api(Canned(trashed))
                    .queryPages("ds-1")
                    .getOrNull()!!
                    .results
                    .single()

            // A remote delete, discovered on pull. Not a tombstone: ADR-036 removed those because
            // nothing local can delete anything.
            assertTrue(page.isGone)
        }

    @Test
    fun unknownFieldsNeverBreakAResponse() =
        runTest {
            // CLAUDE.md §6: "Notion adds fields; an unknown key must never break a sync."
            val withExtras =
                """{"object":"list","results":[],"has_more":false,"a_field_notion_added_later":42,
                   "developer_survey":"https://example.invalid"}"""

            val result = api(Canned(withExtras)).queryPages("ds-1")

            assertEquals(0, result.getOrNull()!!.results.size)
        }

    @Test
    fun anUpdateSendsOnlyThePropertiesItWasGivenKeyedByPropertyId() =
        runTest {
            // docs/notion-schema.md §7: Weight, Sleep and Area are the user's data that this app
            // does not model, and a sync "must never null them out on write". Sending a partial
            // properties object is the whole of that guarantee.
            api(Canned(onePage)).updatePage(
                pageId = "page-1",
                properties = mapOf(readPropertyId to checkbox(true)),
            )

            val request = sent.single()
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("/v1/pages/page-1", request.url.encodedPath)
            val properties = NotionJson.parseToJsonElement(request.bodyText()).jsonObject["properties"]!!.jsonObject
            assertEquals(setOf(readPropertyId), properties.keys)
            assertTrue(
                properties
                    .getValue(readPropertyId)
                    .jsonObject["checkbox"]!!
                    .jsonPrimitive.boolean,
            )
        }

    @Test
    fun aCreatedPageParentsToTheDataSourceNotTheDatabase() =
        runTest {
            // docs/notion-api.md §4, and a consequence of ADR-037 that is easy to miss: a page's
            // parent is a data_source_id now. Parenting to the database is a validation error.
            api(Canned(onePage)).createPage(
                dataSourceId = "ds-1",
                properties = mapOf(datePropertyId to JsonObject(emptyMap())),
            )

            val body = NotionJson.parseToJsonElement(sent.single().bodyText()).jsonObject
            assertEquals("/v1/pages", sent.single().url.encodedPath)
            val parent = body["parent"]!!.jsonObject
            assertEquals("data_source_id", parent["type"]!!.jsonPrimitive.content)
            assertEquals("ds-1", parent["data_source_id"]!!.jsonPrimitive.content)
        }

    @Test
    fun aNotFoundIsTheNotSharedCaseAndIsNotRetried() =
        runTest {
            // ⭐ CLAUDE.md §3.3 and docs/notion-api.md §3.1: 404 means "does not exist OR has not
            // been shared with the connection", and the API gives no way to tell them apart.
            // Retrying it would burn the rate limit to reach the same answer more slowly.
            val result =
                api(Canned("""{"object":"error","status":404,"code":"object_not_found"}""", HttpStatusCode.NotFound))
                    .retrieveDataSource("ds-1")

            assertEquals(NotionError.MissingOrNotShared, result.errorOrNull())
            assertEquals(1, sent.size)
        }

    @Test
    fun aRevokedTokenIsUnauthorisedAndIsNotRetried() =
        runTest {
            val result =
                api(Canned("""{"object":"error","status":401,"code":"unauthorized"}""", HttpStatusCode.Unauthorized))
                    .retrieveDataSource("ds-1")

            // Waiting cannot turn a revoked token into a working one. This needs the user.
            assertEquals(NotionError.Unauthorized, result.errorOrNull())
            assertEquals(1, sent.size)
        }

    @Test
    fun aRateLimitIsRetriedAfterTheServersOwnDelay() =
        runTest {
            val api =
                api(
                    Canned("""{"code":"rate_limited"}""", HttpStatusCode.TooManyRequests, retryAfterSeconds = 7),
                    Canned(emptyListBody),
                )

            val startedAt = testScheduler.currentTime
            val result = api.queryPages("ds-1")

            assertTrue(result is NotionResult.Success)
            assertEquals(2, sent.size)
            // CLAUDE.md §3.5: "honour the server's retry delay on 429". The server knows when the
            // window reopens; our backoff would only be guessing, and guessing low.
            assertTrue(
                "Expected to wait out the server's 7s, waited ${testScheduler.currentTime - startedAt}ms",
                testScheduler.currentTime - startedAt >= 7.seconds.inWholeMilliseconds,
            )
        }

    @Test
    fun aRateLimitThatNeverClearsEndsAsRateLimitedCarryingTheDelay() =
        runTest {
            val result =
                api(
                    Canned("""{"code":"rate_limited"}""", HttpStatusCode.TooManyRequests, retryAfterSeconds = 3),
                    retryPolicy = RetryPolicy(maxAttempts = 2),
                ).queryPages("ds-1")

            assertEquals(NotionError.RateLimited(3.seconds), result.errorOrNull())
            assertEquals(2, sent.size)
        }

    @Test
    fun aServerErrorIsRetriedAndThenReported() =
        runTest {
            // 529 is Notion's own service_overload — a different condition from 503, and one the
            // status range covers without a special case.
            val result =
                api(
                    Canned("""{"code":"service_overload"}""", HttpStatusCode(529, "Service Overload")),
                    retryPolicy = RetryPolicy(maxAttempts = 3),
                ).queryPages("ds-1")

            assertEquals(NotionError.Server(529), result.errorOrNull())
            assertEquals(3, sent.size)
        }

    @Test
    fun noResponseAtAllIsRetriedAndThenReportedAsNetwork() =
        runTest {
            val result =
                api(
                    Canned(failWith = IOException("the radio is off")),
                    retryPolicy = RetryPolicy(maxAttempts = 2),
                ).queryPages("ds-1")

            // CLAUDE.md §1 treats offline as the normal case, not the error case: this must be a
            // value the sync status screen can render, never an exception reaching a coroutine.
            assertTrue(result.errorOrNull() is NotionError.Network)
            assertEquals(2, sent.size)
        }

    @Test
    fun withNoTokenStoredNothingIsSent() =
        runTest {
            val result = api(Canned(emptyListBody), token = false).queryPages("ds-1")

            assertEquals(NotionError.NotConnected, result.errorOrNull())
            // Not merely "it failed": a request without a token is a guaranteed 401 that spends a
            // round trip and a rate-limit slot to learn what the store already knew.
            assertTrue("Expected no request at all, got ${sent.size}", sent.isEmpty())
        }

    @Test
    fun aBodyThatWillNotParseIsMalformedRatherThanACrash() =
        runTest {
            val result = api(Canned("""{"object":"list","results":"not-an-array"}""")).queryPages("ds-1")

            // ignoreUnknownKeys means a NEW field can never land here, so this only fires when a
            // field we depend on changed shape — a version-pin problem, and it should read like one.
            assertTrue(result.errorOrNull() is NotionError.Malformed)
        }

    @Test
    fun aFailureNeverCarriesTheToken() =
        runTest {
            // CLAUDE.md §3.6: the token must not reach a log or a stack trace — and an error object
            // is a thing that gets printed, by a `Log.d("sync failed: $error")` written months from
            // now by somebody who never read §3.6.
            val failures =
                listOf(
                    api(Canned("""{"message":"nope"}""", HttpStatusCode.BadRequest)).queryPages("ds-1"),
                    api(Canned(status = HttpStatusCode.Unauthorized)).queryPages("ds-1"),
                    api(Canned(failWith = IOException("boom")), retryPolicy = RetryPolicy(maxAttempts = 1))
                        .queryPages("ds-1"),
                )

            failures.forEach { failure ->
                val printed = failure.toString()
                assertFalse(printed, printed.contains("ntn_"))
                assertFalse(printed, printed.contains(FakeNotionTokenStore.NOT_A_REAL_TOKEN.value))
            }
        }

    private fun checkbox(value: Boolean) = JsonObject(mapOf("checkbox" to JsonPrimitive(value)))
}
