package com.cristianprincipi.habits.data.remote.notion.dto

import kotlinx.serialization.Serializable

/**
 * Notion's own error body.
 *
 * Every field is optional. The status line is the authority on *what* went wrong — this exists to
 * carry the detail that makes a message useful, and a failure that is not JSON at all (a proxy, a
 * captive portal, an HTML error page) must degrade to "no detail" rather than to a second failure
 * on top of the first.
 */
@Serializable
data class NotionErrorDto(
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
)
