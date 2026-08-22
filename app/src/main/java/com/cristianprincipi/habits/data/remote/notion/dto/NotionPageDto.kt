package com.cristianprincipi.habits.data.remote.notion.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A page — which in this database is **a day** (`docs/notion-schema.md` §1).
 *
 * Two things about this type are easy to get wrong, and both are asymmetries:
 *
 * **1. [properties] is keyed by property NAME on the way in, and by property ID on the way out.**
 * A response maps `"📖 Read" -> {...}`; a write sends `"ZI%40W" -> {...}` (ADR-038,
 * `notion-api.md` §9). So reading a habit's value means finding the entry whose *inner* `id`
 * matches the mapping — never looking it up by the map key, which is the user-editable name that
 * `CLAUDE.md` §3.4 forbids us to depend on.
 *
 * **2. The property values stay as raw [JsonObject] here.** Decoding them is M4.6's job, and the
 * seam is deliberate: this type is about *transport*, and every property carries a different value
 * shape. Each object holds its own `id` and `type` alongside the value (`notion-api.md` §8).
 *
 * Timestamps are `String`, not `java.time.Instant`, for the same reason: `kotlinx.serialization` has
 * no built-in `Instant` serializer and ADR-015 bans `kotlinx-datetime`. Wire types stay on the wire;
 * parsing belongs to the mapper.
 */
@Serializable
data class NotionPageDto(
    val id: String,
    @SerialName("last_edited_time") val lastEditedTime: String,
    @SerialName("created_time") val createdTime: String? = null,
    val properties: Map<String, JsonObject> = emptyMap(),
    @SerialName("in_trash") val inTrash: Boolean = false,
    @SerialName("is_archived") val isArchived: Boolean = false,
) {
    /**
     * Whether Notion considers this page gone.
     *
     * **Both flags are read, because the docs carry both.** `docs/notion-api.md` §2.1 records that
     * `archived` was replaced by `in_trash` in the pinned version, yet the live page object shows
     * `in_trash` *and* `is_archived` side by side (§10, resolved for responses on 2026-08-22). Which
     * one a given deployment sets is not worth betting a pull on, and `||` costs nothing.
     *
     * This is the **remote** delete `CLAUDE.md` §3.5 distinguishes from a local one: something to
     * reconcile on pull, not a tombstone we owe upstream. ADR-036 means nothing here ever writes it.
     */
    val isGone: Boolean
        get() = inTrash || isArchived
}
