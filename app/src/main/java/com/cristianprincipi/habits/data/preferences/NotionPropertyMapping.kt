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
 * A number column the app reads and averages, and the unit the user typed for it (M15.4).
 *
 * ⭐⭐ **A measure is not a habit, and the type system is where that is enforced.** `CLAUDE.md`
 * §3.5 and the M15.4 audit both land on the same argument: keeping these ids **out of**
 * [NotionPropertyMapping.habitPropertyIds] is what makes the exclusion structural rather than a
 * filter every screen has to remember. Week pills, the Month and Year dot grids and the streak
 * tiles all iterate habits; none of them can accidentally draw a measure, because a measure is not
 * in the collection they read.
 *
 * ⭐ **It is also what keeps the push path free.** `SyncEngine.pushDay` builds its request body by
 * mapping `MappedHabits.properties` to `NotionPropertyValue.Checkbox`, so a property id that never
 * enters that set has **no code path** to a write payload — which is the whole of what the user's
 * *"read only"* asked for, obtained by construction rather than by a guard somebody could remove.
 *
 * @param unit blank for "show the bare number". ⚠⚠ **Typed by the user, because it cannot be
 *   derived**: `NotionPropertySchemaDto` carries no number format, and §3.4 forbids reading "kg"
 *   off a property *named* `Weight`.
 */
data class NotionMeasure(
    val id: NotionPropertyId,
    val unit: String,
) {
    init {
        require(id.value.isNotBlank()) { "A measure needs a property id." }
    }
}

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
 * 4. **[measures]** — which of the number properties to average on the Stats screen (M15.4).
 *
 * **The last two are subsets, and the leftovers are still the point.** `Area` and `Progress` sit in
 * the same schema and are neither; §7 requires the sync to read pages "without touching them, and
 * never null them out on write". Anything absent from both sets is invisible to the app: not
 * imported, not rendered, and — because `NotionApi.updatePage` sends only the properties it is
 * given — not written.
 *
 * ⚠ **Being in [measures] buys visibility and nothing else.** `Weight` and `Sleep` used to be
 * leftovers and are now mapped, and §7 still holds for them exactly: a measure is pulled, stored
 * and averaged, and there is no code path that can write one back (see [NotionMeasure]).
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
 * Room's, and one copy of a fact is enough.
 *
 * ⚠ **[NotionMeasure.unit] looks like it breaks that rule and does not.** A unit is app-local
 * presentation, like a colour — so by the paragraph above it belongs in Room. It is here because of
 * *when* it is collected: the user types it in the mapping screen, at the moment they tick the
 * column, and on a first run **no row exists yet** to put it on. This follows `habitPropertyIds`'
 * own precedent rather than the colour's — that field carries the user's order and `HabitImporter`
 * copies it onto `HabitEntity.sortOrder`. The mapping is the input of record; Room gets the working
 * copy. The sync cursor: that is
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
    /**
     * The number properties to average on the Stats screen, in the order they should be shown.
     *
     * ⚠⚠ **A `List` and not a `Set`, and that is a real loss that has to be paid for below.**
     * [habitPropertyIds] gets ADR-035's duplicate-id guarantee for free from being a `Set`; this
     * cannot, because a [NotionMeasure] carries a unit as well as an id, so two entries naming one
     * property with different units are distinct set members and a `Set` would happily hold both.
     * The `require` in [init] is what replaces the type-level guarantee — it is not belt-and-braces,
     * it is the only thing standing there.
     *
     * Empty is the normal state: every mapping saved before M15.4 has none, and a user who wants no
     * averages maps none. ⚠ **Never pre-ticked**, unlike the habits — see `withMapping`.
     */
    val measures: List<NotionMeasure> = emptyList(),
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
        // The three below are what a `Set` would have given [measures] for nothing. A property
        // that is two things at once makes every pull ambiguous in exactly ADR-035's way, and the
        // day key being read as a measure would average a column of dates.
        require(measures.map(NotionMeasure::id).distinct().size == measures.size) {
            "The mapping names the same measure property twice."
        }
        require(measures.none { it.id in habitPropertyIds }) {
            "A property cannot be both a habit and a measure."
        }
        require(measures.none { it.id == dayKeyPropertyId }) {
            "The day-key property cannot also be a measure: ${dayKeyPropertyId.value}"
        }
    }
}
