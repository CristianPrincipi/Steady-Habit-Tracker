package com.cristianprincipi.habits.data.remote.notion.mapper

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate

/**
 * One property's value, decoded off a page.
 *
 * A closed set, so the sync engine reads a habit by matching on a case rather than by reaching into
 * a `JsonObject` and hoping. `NotionPropertyCodec` produces these; nothing else should.
 *
 * **[Writable] is where `CLAUDE.md` §3.2's "prevent that mistake rather than failing at sync time"
 * actually lands.** A formula cannot be written to Notion, and in this vocabulary a formula cannot
 * be turned into a request body either — there is no method to call. The same is true of the number,
 * text and multi-select values this app reads but does not own: `docs/notion-schema.md` §7 says the
 * sync "must read pages without touching them, and must never null them out on write", and the
 * cheapest way to guarantee that is to make writing them unexpressible.
 */
sealed interface NotionPropertyValue {
    /**
     * The values this app can send back to Notion. **Exactly two, and that is the whole write
     * surface of v1**: a habit's checkbox, and the date that identifies the day.
     */
    sealed interface Writable : NotionPropertyValue {
        /**
         * This value as the JSON Notion expects under a property key.
         *
         * The key itself is the caller's business — it is the property **id** (ADR-038), and
         * `NotionApi.updatePage` takes the whole map.
         */
        fun toWire(): JsonObject
    }

    /** ⭐ A habit, done or not, on the day this page is. Never null: Notion sends `false`. */
    data class Checkbox(
        val checked: Boolean,
    ) : Writable {
        override fun toWire(): JsonObject = buildJsonObject { put("checkbox", checked) }
    }

    /**
     * ⭐ The day key (`docs/notion-schema.md` §2).
     *
     * A `LocalDate`, never an instant — `CLAUDE.md` §6: "a habit checked at 23:00 belongs to that
     * day regardless of timezone". The user's `Date` property is date-only with a null time zone,
     * so this is a lossless reading of it today; [NotionPropertyCodec] also copes with the day the
     * property gains a time, by keeping the date as written in whatever offset came with it.
     *
     * [end] exists because Notion dates can be ranges. Nothing in v1 creates one.
     */
    data class DateValue(
        val start: LocalDate,
        val end: LocalDate? = null,
    ) : Writable {
        override fun toWire(): JsonObject =
            buildJsonObject {
                putJsonObject("date") {
                    put("start", start.toString())
                    // Omitted rather than sent as null when absent. A `null` end would be Notion's
                    // instruction to clear one, and this app never edits a range it did not create.
                    end?.let { put("end", it.toString()) }
                }
            }
    }

    /** A title or rich-text run, flattened to plain text. Read for display; never written. */
    data class Text(
        val text: String,
    ) : NotionPropertyValue

    /**
     * `Weight` and `Sleep` — the user's daily metrics, which v1 reads and does not model.
     *
     * `Double`, because Notion sends a bare JSON number: `84`, not the `84.00` its display
     * formatting shows (`docs/notion-schema.md` §2.4). Never parse the display string.
     */
    data class Number(
        val value: Double,
    ) : NotionPropertyValue

    /** `Area`. Always a list, even holding one option — parse it as a list of one, never a scalar. */
    data class MultiSelect(
        val names: List<String>,
    ) : NotionPropertyValue

    /**
     * A computed value — `Progress`, in this database.
     *
     * [result] reuses this same vocabulary because a formula result *is* one of these kinds: a
     * boolean result arrives as [Checkbox], a number as [Number], a string as [Text], a date as
     * [DateValue]. Reusing them keeps one decoder instead of two parallel ones.
     *
     * **Not [Writable], and that is the point.** `docs/notion-schema.md` §2.2: the app computes its
     * own daily progress and "must never attempt to write" this. Notion recomputes it from the
     * checkboxes we do write anyway.
     */
    data class Formula(
        val result: NotionPropertyValue,
    ) : NotionPropertyValue

    /** The property is on the page but holds nothing. A `null` date, an unset number. */
    data object Empty : NotionPropertyValue

    /**
     * There is a value here and this build cannot turn it into one of the cases above.
     *
     * Two causes, deliberately one case: a property **type** v1 does not decode (a rollup, a
     * relation, a type Notion added last week), or a value of a type it does decode whose contents
     * would not parse — a `date` whose `start` is not a date being the one that would matter.
     *
     * It is never an exception. `CLAUDE.md` §6 requires that an unknown key never break a sync, and
     * the same has to be true of an unknown value: a pull that dies on one odd column takes the
     * other four habits with it.
     *
     * @param wireType the `type` string exactly as Notion sent it, so the mapping UI can name it.
     */
    data class Unreadable(
        val wireType: String,
    ) : NotionPropertyValue
}
