package com.cristianprincipi.habits.data.remote.notion.dto

import com.cristianprincipi.habits.data.remote.notion.NotionJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pagination envelope, and the one place `docs/notion-api.md` §5 says in bold that the obvious
 * reading is wrong.
 *
 * These decode real response shapes rather than constructing the data class directly: the mapping
 * from `has_more` / `next_cursor` / `request_status` onto [NotionListDto.cursorForNextPage] and
 * [NotionListDto.isTruncated] is the thing under test, and half of it is the `@SerialName`s.
 */
class NotionListDtoTest {
    private fun decode(json: String): NotionListDto<NotionPageDto> =
        NotionJson.decodeFromString(NotionListDto.serializer(NotionPageDto.serializer()), json)

    @Test
    fun aCompletePageOffersNoCursor() {
        val list = decode("""{"object":"list","results":[],"has_more":false,"next_cursor":null}""")

        assertNull(list.cursorForNextPage)
        assertFalse(list.isTruncated)
    }

    @Test
    fun aPartialPageOffersTheCursorToContinueWith() {
        val list = decode("""{"object":"list","results":[],"has_more":true,"next_cursor":"cursor-2"}""")

        assertEquals("cursor-2", list.cursorForNextPage)
        assertFalse(list.isTruncated)
    }

    @Test
    fun aCursorWithoutHasMoreIsIgnored() {
        // "next_cursor only appears when has_more is true" — so a cursor arriving alongside
        // has_more:false is a contradiction, and following it would page forever.
        val list = decode("""{"object":"list","results":[],"has_more":false,"next_cursor":"cursor-2"}""")

        assertNull(list.cursorForNextPage)
    }

    @Test
    fun theTenThousandResultCeilingIsVisibleEvenThoughHasMoreSaysFalse() {
        // ⭐ docs/notion-api.md §5, the trap: past 10,000 results `has_more` goes FALSE and only
        // `request_status` says the answer was cut short. A pull that trusts `has_more` alone stops
        // there and believes it finished — silently, with a local database missing the remainder.
        val list =
            decode(
                """{"object":"list","results":[],"has_more":false,"next_cursor":null,
                   "request_status":{"type":"incomplete","incomplete_reason":"query_result_limit_reached"}}""",
            )

        assertNull(list.cursorForNextPage)
        assertTrue("has_more was false, so only request_status can say this was truncated", list.isTruncated)
        assertEquals("query_result_limit_reached", list.requestStatus?.incompleteReason)
    }

    @Test
    fun anExplicitlyCompleteRequestStatusIsNotTruncation() {
        val list =
            decode("""{"object":"list","results":[],"has_more":false,"request_status":{"type":"complete"}}""")

        assertFalse(list.isTruncated)
    }

    @Test
    fun anEnvelopeWithNoRequestStatusAtAllIsTreatedAsComplete() {
        // Endpoints older than the field, and the search endpoint on a small workspace, omit it.
        // Absent must mean "fine", never "unknown, therefore suspect" — the alternative is a pull
        // that refuses to finish on every list endpoint that does not send it.
        val list = decode("""{"object":"list","results":[],"has_more":false}""")

        assertFalse(list.isTruncated)
    }
}
