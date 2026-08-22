package com.cristianprincipi.habits.data.remote.notion.mapper

import com.cristianprincipi.habits.data.remote.notion.dto.NotionPageDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * Turns one raw page property into a [NotionPropertyValue].
 *
 * `NotionPageDto` deliberately leaves property values as `JsonObject` — M4.5 drew the seam there
 * because every property type carries a different shape. This is the other side of it.
 *
 * **Nothing here throws, and that is a requirement rather than a nicety.** A pull reads five habits
 * off one page; a decoder that threw on the sixth column would take the five with it. Every
 * unreadable value becomes [NotionPropertyValue.Unreadable] and the rest of the page survives.
 *
 * The shapes come from the live page-property-values reference, read 2026-08-22 (`CLAUDE.md` §3.2 —
 * not from memory), and are recorded in `docs/notion-api.md` §8 and §13.
 */
object NotionPropertyCodec {
    /**
     * @param property one entry from a page's `properties` map — `{"id": …, "type": …, <type>: …}`.
     *
     * The type is read from the value itself rather than passed in, because that is where Notion
     * puts it: every page property value carries its own `id` and `type` alongside the value.
     */
    fun decode(property: JsonObject): NotionPropertyValue {
        val wireType = property.string("type") ?: return NotionPropertyValue.Unreadable("")
        return when (NotionPropertyType.fromWire(wireType)) {
            NotionPropertyType.Checkbox -> {
                property.boolean("checkbox")?.let(NotionPropertyValue::Checkbox)
                    ?: NotionPropertyValue.Unreadable(wireType)
            }

            NotionPropertyType.Date -> {
                decodeDate(property, wireType)
            }

            NotionPropertyType.Title -> {
                decodeText(property, "title")
            }

            NotionPropertyType.RichText -> {
                decodeText(property, "rich_text")
            }

            NotionPropertyType.Number -> {
                property.number("number")?.let(NotionPropertyValue::Number) ?: NotionPropertyValue.Empty
            }

            NotionPropertyType.MultiSelect -> {
                decodeMultiSelect(property)
            }

            NotionPropertyType.Formula -> {
                decodeFormula(property, wireType)
            }

            // Everything else, and every type this build has never heard of. `docs/notion-schema.md`
            // §2 says the user's database holds none of them; if one appears, the mapping screen
            // names it and the sync steps over it.
            else -> {
                NotionPropertyValue.Unreadable(wireType)
            }
        }
    }

    /**
     * A checkbox is never null, so an absent boolean means the shape changed rather than the value
     * being unset — hence [NotionPropertyValue.Unreadable] above and [NotionPropertyValue.Empty]
     * here for the types that genuinely can hold nothing.
     */
    private fun decodeText(
        property: JsonObject,
        key: String,
    ): NotionPropertyValue {
        val runs = property.array(key) ?: return NotionPropertyValue.Empty
        // `plain_text` is Notion's own flattening of the run, so nothing here has to understand
        // mentions, equations or annotations to read a title.
        return NotionPropertyValue.Text(
            runs.joinToString(separator = "") { it.asObject()?.string("plain_text").orEmpty() },
        )
    }

    /** `docs/notion-schema.md` §2.4: an array even when one option is selected. Never a scalar. */
    private fun decodeMultiSelect(property: JsonObject): NotionPropertyValue {
        val options = property.array("multi_select") ?: return NotionPropertyValue.Empty
        return NotionPropertyValue.MultiSelect(options.mapNotNull { it.asObject()?.string("name") })
    }

    /**
     * The day key.
     *
     * A `start` that is present but will not parse is [NotionPropertyValue.Unreadable], not
     * [NotionPropertyValue.Empty]. They mean different things to the sync engine: empty is a page
     * the user has not dated, unreadable is a page whose date we could not understand, and the
     * second deserves to be noticed rather than silently treated as the first.
     *
     * An unparseable `end` is dropped instead — v1 never creates a range, so the end of one is
     * decoration, and losing it must not cost us the day.
     */
    private fun decodeDate(
        property: JsonObject,
        wireType: String,
    ): NotionPropertyValue {
        val date = property.obj("date")
        val startText = date?.string("start")
        val start = startText?.let(::parseDay)
        return when {
            startText == null -> NotionPropertyValue.Empty
            start == null -> NotionPropertyValue.Unreadable(wireType)
            else -> NotionPropertyValue.DateValue(start, date.string("end")?.let(::parseDay))
        }
    }

    /**
     * `{"formula": {"type": "number", "number": 56}}` — the inner `type` names which of the four
     * result kinds it is, and each maps onto the value case that already models that kind.
     */
    private fun decodeFormula(
        property: JsonObject,
        wireType: String,
    ): NotionPropertyValue {
        val formula = property.obj("formula") ?: return NotionPropertyValue.Empty
        val resultType = formula.string("type").orEmpty()
        val result =
            when (resultType) {
                "boolean" -> formula.boolean("boolean")?.let(NotionPropertyValue::Checkbox) ?: NotionPropertyValue.Empty
                "number" -> formula.number("number")?.let(NotionPropertyValue::Number) ?: NotionPropertyValue.Empty
                "string" -> formula.string("string")?.let(NotionPropertyValue::Text) ?: NotionPropertyValue.Empty
                "date" -> decodeDate(formula, resultType)
                else -> NotionPropertyValue.Unreadable(resultType.ifEmpty { wireType })
            }
        return NotionPropertyValue.Formula(result)
    }
}

/**
 * ⭐ Finds a property on a page **by its id**, which is not the same as looking it up by key.
 *
 * A page's `properties` map is keyed by property **name**; the id lives inside each value. The
 * mapping is keyed on the id (ADR-038), because the id survives a rename and the name does not — so
 * every read has to scan for the id rather than index by the key sitting right there.
 *
 * That is the single easiest mistake to make in this layer, and it fails in the worst way: renaming
 * `📖 Read` in Notion would look exactly like deleting the habit. `CLAUDE.md` §3.4 forbids depending
 * on a property name anywhere, and this is the function that keeps that promise.
 *
 * @return `null` when the page has no such property — a page written before the habit existed.
 */
fun NotionPageDto.rawProperty(propertyId: String): JsonObject? =
    properties.values.firstOrNull { value -> value.string("id") == propertyId }

/** [rawProperty], decoded. `null` still means the property is absent from this page. */
fun NotionPageDto.property(propertyId: String): NotionPropertyValue? =
    rawProperty(propertyId)?.let(NotionPropertyCodec::decode)

/**
 * Reads a Notion date string as the day it names.
 *
 * `start` "accepts dates with optional times in ISO 8601 format", so three shapes are possible and
 * all three are tried: `2026-08-19`, `2026-08-19T10:00:00Z` (or with an offset), and a local
 * date-time with none. `docs/notion-schema.md` §2.4 says the user's property is date-only today, and
 * the day it stops being is not the day sync should break.
 *
 * **The date is taken as written, in whatever offset came with it** — never converted to the
 * device's zone. `CLAUDE.md` §6: a habit checked at 23:00 belongs to that day regardless of
 * timezone, and re-zoning the value would move some of those days by one.
 */
private fun parseDay(raw: String): LocalDate? =
    runCatching { LocalDate.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw).toLocalDate() }.getOrNull()

// The accessors below are total: a missing key, a null, or a value of the wrong kind all return
// null rather than throwing. kotlinx's own `jsonObject` / `jsonPrimitive` throw, which is the
// opposite of what a decoder reading someone else's payload needs.

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
