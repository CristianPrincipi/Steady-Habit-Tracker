package com.cristianprincipi.habits.data.remote.notion.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The envelope every Notion list endpoint returns — search, query, everything.
 *
 * `docs/notion-api.md` §5: the pagination fields are identical across all of them, which is why one
 * generic type covers the lot.
 *
 * **The two raw flags are deliberately not what callers read.** [cursorForNextPage] and
 * [isTruncated] exist because reading `has_more` directly is the mistake §5 warns about in bold,
 * and a derived property is a cheaper defence than a comment somebody has to notice.
 */
@Serializable
data class NotionListDto<T>(
    val results: List<T> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("request_status") val requestStatus: NotionRequestStatusDto? = null,
) {
    /**
     * The cursor to send as `start_cursor` for the next page, or `null` when this was the last one.
     *
     * Folds the two fields together the way the docs describe them — "`next_cursor` only appears
     * when `has_more` is true" — so a loop reads `while (cursor != null)` and cannot accidentally
     * depend on only one of them.
     */
    val cursorForNextPage: String?
        get() = nextCursor?.takeIf { hasMore }

    /**
     * ⭐ **The result set was cut short, and `has_more` said nothing about it.**
     *
     * `docs/notion-api.md` §5: the query endpoint returns at most 10,000 results, and past that
     * `has_more` goes **false** while `request_status` becomes
     * `{"type": "incomplete", "incomplete_reason": "query_result_limit_reached"}`. A pull that
     * trusts `has_more` alone stops there and believes it finished — silently, and with a local
     * database that is missing everything after the ceiling.
     *
     * At one page per day this app needs about 27 years to reach it, and the incremental pull
     * (`notion-api.md` §7) makes it unreachable in practice. It is surfaced anyway because the cost
     * is one boolean and the failure it prevents is invisible.
     */
    val isTruncated: Boolean
        get() = requestStatus?.type == INCOMPLETE
}

/**
 * A top-level constant rather than a `private companion object`, and that is not arbitrary.
 *
 * `@Serializable` generates `serializer()` **onto the class's companion**, so declaring the
 * companion `private` makes the generated serializer private with it (ADR-043). Nothing warns you:
 * the class compiles, and the breakage arrives either as a compile error at the call site or — if
 * the serializer is looked up reflectively — as a runtime `IllegalAccessException` in the middle of
 * a sync. **Never give a `@Serializable` class a private companion.**
 */
private const val INCOMPLETE = "incomplete"

/**
 * Whether the endpoint returned everything it was asked for.
 *
 * `incompleteReason` is carried but not interpreted: the only value documented today is
 * `query_result_limit_reached`, and inventing a `when` over a one-member set would be inventing the
 * other members too.
 */
@Serializable
data class NotionRequestStatusDto(
    val type: String,
    @SerialName("incomplete_reason") val incompleteReason: String? = null,
)
