package com.cristianprincipi.habits.data.preferences

/**
 * A Notion property's identity — **the id, never the name**.
 *
 * `docs/notion-api.md` §9 (ADR-038): the id *"remains constant when the property name changes"* and
 * *"may be used in place of name when creating or updating pages"*. Both halves matter here. The
 * first is why the mapping is keyed on this and survives the user renaming `📖 Read` to
 * `📖 Reading`; the second is why the push path can write with it and never look a name up.
 *
 * **A type rather than a `String`, for the same reason [com.cristianprincipi.habits.data.remote.notion.auth.NotionToken]
 * is one.** The mistake this codebase is most exposed to is passing a property *name* where an id
 * belongs: both are `String`, both are short, and a page's `properties` map is keyed by name while
 * writes are keyed by id (`NotionPageDto`'s KDoc). A name-keyed read reports a renamed habit as a
 * deleted one — the worst failure available, because the recovery is "restore history" rather than
 * "re-map". Making the two different types is what stops that at compile time.
 *
 * **Stored verbatim.** Ids arrive percent-encoded — `"ZI%40W"`, `"M%3BBw"` — and nothing here
 * decodes, trims or normalises them (`docs/notion-schema.md` §6.1). The string Notion sent is the
 * string that goes back.
 */
@JvmInline
value class NotionPropertyId(
    val value: String,
)

/**
 * The data source the habits live in (ADR-037).
 *
 * **Not a database id**, though the user only ever sees the database. `docs/notion-api.md` §1: a
 * database now *contains* data sources, and the properties — which in this app are the habits —
 * belong to the data source. Every call in `NotionApi` that touches the schema or the pages takes
 * this id, which is why it is what gets persisted.
 */
@JvmInline
value class NotionDataSourceId(
    val value: String,
)

/**
 * The database that *contains* the data source — the id that appears in a Notion URL.
 *
 * ⚠ **A different type from [NotionDataSourceId], and the separation is the point.** Both are
 * UUIDv4s and neither works where the other belongs: every API call in this app takes the data
 * source id, and every `notion.so` link is built from the database id. This app once held only the
 * first, which is why `NotionDatabaseLink` could parse a URL and nothing could produce one.
 *
 * Learned rather than chosen: the user picks a *database* in the wizard, but what the wizard keeps
 * is the data source, so this arrives later — off the `parent` of the schema the sync engine already
 * fetches (`NotionDataSourceDto.databaseId`).
 */
@JvmInline
value class NotionDatabaseId(
    val value: String,
)

/**
 * What the user told us their Notion database means. **The whole of it.**
 *
 * `CLAUDE.md` §3.4 describes this as "one dropdown per domain field" — which property is the habit
 * name, which one records a completion. That description assumed one habit per row, and
 * `docs/notion-schema.md` §4.3 records that the real database is not shaped that way: there is one
 * page per **day**, and each habit is a **checkbox property** on it. So the mapping is not a set of
 * field dropdowns. It is three answers:
 *
 * 1. **[dataSourceId]** — where the schema and the pages live.
 * 2. **[dayKeyPropertyId]** — the `Date` property that says which day a page *is*. Not the title
 *    (unused in this database) and not `created_time` (`docs/notion-schema.md` §2).
 * 3. **[habitPropertyIds]** — which of the checkbox properties are habits.
 *
 * **The third one is a subset, and the leftovers are the point.** `Weight`, `Sleep`, `Area` and
 * `Progress` sit in the same schema and are not habits; §7 requires the sync to read pages "without
 * touching them, and never null them out on write". Anything absent from this set is invisible to
 * the app: not imported, not rendered, and — because `NotionApi.updatePage` sends only the
 * properties it is given — not written.
 *
 * **No property names anywhere.** Not stored, not defaulted, not suggested. `CLAUDE.md` §3.4 forbids
 * hardcoding one, and this type is the reason nothing needs to: the display names come from the
 * schema at the moment they are shown, and from `HabitEntity.notionPropertyName` when the schema is
 * not to hand.
 *
 * ⭐ **[databaseId] used to be on the "deliberately not here" list, and M7.5 gave it a reader.** The
 * argument was sound while it lasted: every call past setup takes the data source id, so a database
 * id would have been a second identity to hold consistent for nobody. Then the manage-habits screen
 * needed to *link the user to their own database*, which is the one thing the data source id cannot
 * do. It is stored beside the mapping, not instead of it, and never sent anywhere.
 *
 * **What is still deliberately *not* here.** The habits' names, colours and start dates: those are
 * Room's, and one copy of a fact is enough. The sync cursor: that is
 * [com.cristianprincipi.habits.domain.repository.SettingsRepository]'s `lastSyncedAt`, and a
 * mapping that changed when a sync finished would invalidate a cache the user never touched.
 */
data class NotionPropertyMapping(
    val dataSourceId: NotionDataSourceId,
    /** The `Date` property. Date-only in this database; `NotionPropertyCodec` copes if it gains a time. */
    val dayKeyPropertyId: NotionPropertyId,
    /**
     * The checkbox properties that are habits, in the order they should be shown.
     *
     * **A `Set`, and that is ADR-035's fix.** Two habits claiming one property id make every pull
     * ambiguous, and the database will not catch it: Room's `@Upsert` responds to that unique-index
     * conflict by writing nothing and throwing nothing, so the second habit vanishes in silence.
     * The import in M4.8 builds one habit per entry here, so a set makes the duplicate
     * unrepresentable before it can reach a table that would swallow it.
     *
     * **Iteration order is insertion order** — `setOf` documents that its result iterates "in the
     * order they were specified" — which is what `HabitEntity.sortOrder` is assigned from. Storing
     * the user's order is why this is not a `HashSet`.
     *
     * May be empty. A connection with no habits chosen yet syncs nothing, which is a coherent state
     * during setup; requiring at least one belongs to the screen that collects them (M4.12), where
     * it can say so.
     */
    val habitPropertyIds: Set<NotionPropertyId>,
    /**
     * Where this data source's database lives, for the link out to Notion. `null` until learned.
     *
     * **Nullable because it is learned rather than chosen**, and because every mapping saved before
     * M7.5 has none. The wizard cannot fill it in — it keeps the data source — so the first sync
     * after a connection is what supplies it (`SyncEngine`), and a screen that wants it says so by
     * disabling its control rather than by waiting.
     *
     * It rides with the mapping so that it dies with the mapping: a disconnect wipes both, and
     * re-mapping onto a different database cannot leave a link pointing at the old one.
     */
    val databaseId: NotionDatabaseId? = null,
) {
    init {
        // These are `require`s rather than a nullable factory because every construction site is
        // either the setup screen — which has just read these ids off a schema — or the store,
        // which catches. A blank id is not a user mistake to report; it is a bug or a corrupt file.
        require(dataSourceId.value.isNotBlank()) { "A mapping needs a data source id." }
        require(dayKeyPropertyId.value.isNotBlank()) { "A mapping needs the day-key property id." }
        require(habitPropertyIds.none { it.value.isBlank() }) { "A habit property id cannot be blank." }
        require(dayKeyPropertyId !in habitPropertyIds) {
            "The day-key property cannot also be a habit: ${dayKeyPropertyId.value}"
        }
        require(databaseId == null || databaseId.value.isNotBlank()) {
            "A database id, once known, cannot be blank."
        }
    }
}
