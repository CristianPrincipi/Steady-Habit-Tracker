package com.cristianprincipi.habits.data.remote.notion

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration the Notion client reads and writes with.
 *
 * Shared between the production client and every test, so that a test proving a payload is proving
 * the payload that actually goes out. Three settings, each one load-bearing — see the comments.
 */
val NotionJson: Json =
    Json {
        // CLAUDE.md §6, verbatim: "Notion adds fields; an unknown key must never break a sync."
        // Without this the first field Notion adds to a page object fails every sync at once, and
        // the symptom looks like a network problem rather than a parsing one.
        ignoreUnknownKeys = true

        // Defaults are SENT, because several of ours are the request's meaning. `SearchFilter.value
        // = "data_source"` and `LastEditedTimeFilter.timestamp` are defaults in Kotlin and required
        // fields on the wire. At kotlinx's default of `false` they would be omitted, and the query
        // would quietly become a different query.
        encodeDefaults = true

        // Nulls are OMITTED, which is what keeps a partial page update partial.
        // docs/notion-schema.md §7: `Weight`, `Sleep` and `Area` are the user's data that this app
        // does not model and "must never null out on write". Together with encodeDefaults this
        // means an unset optional disappears from the body rather than arriving as an explicit
        // `null` — which to Notion means "clear this".
        explicitNulls = false
    }
