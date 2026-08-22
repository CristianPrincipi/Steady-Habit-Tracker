package com.cristianprincipi.habits.data.remote.notion.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A database — which after ADR-037 is a **container**, not the thing with the properties on it.
 *
 * The app fetches one only to get from a database id, which is what a Notion URL contains and what
 * a user can copy, to the [dataSources] entry the rest of the client actually talks to. Everything
 * past that point is a data source id.
 */
@Serializable
data class NotionDatabaseDto(
    val id: String,
    val title: List<NotionRichTextDto> = emptyList(),
    @SerialName("data_sources") val dataSources: List<NotionDataSourceRefDto> = emptyList(),
) {
    val displayTitle: String
        get() = title.joinToString(separator = "") { run -> run.plainText }
}

/**
 * A stub: "List of child data sources, each of which is a JSON object with an `id` and `name`."
 *
 * Deliberately a different type from [NotionDataSourceDto] even though both describe a data source.
 * This one has a plain-string `name` and no properties; retrieving the full object is a second call.
 * Merging them into one nullable-everything type would hide that a stub cannot answer schema
 * questions.
 */
@Serializable
data class NotionDataSourceRefDto(
    val id: String,
    val name: String = "",
)
