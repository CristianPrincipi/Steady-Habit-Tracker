package com.cristianprincipi.habits.data.remote.notion

/**
 * Turns what a user can copy into an id the API will take.
 *
 * The setup screen's fallback path: a database picker is the ideal (`docs/design-spec.md` §6), but a
 * user whose workspace is large, or whose database was shared thirty seconds ago and has not reached
 * Notion's search index yet, has a **link** and nothing else.
 *
 * Every rule below is quoted in `docs/notion-api.md` §16, read from the live reference in M4.12 —
 * none of it is remembered (`CLAUDE.md` §3.2).
 */
object NotionDatabaseLink {
    /**
     * @return the id with dashes stripped, or `null` if there is no id-shaped thing in [raw].
     *
     * Three rules, in this order, and the order is the whole of it:
     *
     * 1. ⭐ **Everything from `?` is dropped first.** The reference puts the id "between the slash
     *    following the workspace name and **the question mark**", and what sits after that question
     *    mark in a real database URL is `v=<32 hex>` — the **view** id, which has exactly the same
     *    shape and is not the database. Matching before stripping finds the decoy.
     * 2. **The last match wins.** Notion prefixes the id with a title slug — `My-Habits-<32 hex>` —
     *    whose words are themselves dash-separated, so anchoring at the start is wrong and a slug
     *    word that happens to be hex would otherwise be picked up.
     * 3. **Dashes come off.** The API accepts hyphenated and un-hyphenated alike, so normalising to
     *    one shape means one shape is ever sent.
     *
     * **A bare id parses too**, hyphenated or not, because that is the other thing a user pastes.
     *
     * ⚠ **A page URL parses cleanly and is still the wrong id**, and nothing local can tell:
     * a page and a database are both UUIDv4. It surfaces as `404`, which
     * [NotionError.MissingOrNotShared] already names for the more likely cause.
     */
    fun idOrNull(raw: String): String? {
        val beforeQuery = raw.substringBefore('?').substringBefore('#')
        val match = UUID.findAll(beforeQuery).lastOrNull() ?: return null
        return match.value.replace("-", "")
    }

    /**
     * The inverse: a link to a database, from its id.
     *
     * ⭐ **The only URL this app ever produces**, and it is produced to be *handed to the browser*,
     * never fetched — `CLAUDE.md` §8 restricts the hosts the app contacts, and `api.notion.com`
     * remains the only one. Opening `notion.so` is the user leaving, the same as any link.
     *
     * Dashes come off for the same reason [idOrNull] strips them: §16 records that a Notion URL
     * carries the un-hyphenated form, so this produces what the user would have copied. No `?v=`
     * view id is appended — we have never seen one, and Notion opens the database's default view
     * without it.
     *
     * ⚠ **Nothing validates that [databaseId] is a database.** It comes from the `parent` of the
     * schema Notion itself returned, so the only way to get this wrong is for Notion to have lied.
     */
    fun urlFor(databaseId: String): String = "$WORKSPACE/${databaseId.replace("-", "")}"

    private const val WORKSPACE = "https://www.notion.so"

    /**
     * A UUIDv4 in either shape: `8-4-4-4-12` hex with the dashes optional.
     *
     * Written out group by group rather than as `[0-9a-fA-F-]{32,36}`, because the loose version
     * would match a dash-heavy slug of the right length and hand back nonsense the API would reject
     * as an invalid id — a worse error to show the user than "that does not look like a link".
     */
    private val UUID =
        Regex("[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}")
}
