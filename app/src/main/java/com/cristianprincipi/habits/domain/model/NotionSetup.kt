package com.cristianprincipi.habits.domain.model

/**
 * A Notion database the connection can see, as the setup screen needs to show it.
 *
 * **It is a data source, not a database** (ADR-037) — but the user has never heard that word and
 * never needs to. They see one thing called `Habits` in Notion, and this is it. [id] is the id every
 * later call takes and the one that gets persisted; the naming here follows what the *user* sees,
 * because this type exists to be rendered.
 *
 * [title] can be empty. An untitled database is legal in Notion, and the screen says so rather than
 * inventing a name for it.
 */
data class NotionSource(
    val id: String,
    val title: String,
)

/**
 * One column of the user's database, and whether it can be part of the mapping.
 *
 * `CLAUDE.md` §3.4: the schema is **user data**, so nothing about this is hardcoded — the whole list
 * arrives from the live database each time setup runs. [name] carries emoji and is the user's to
 * edit (`docs/notion-schema.md` §2.3); [id] is the stable thing the mapping is keyed on (ADR-038).
 *
 * @param typeName Notion's own type string, raw and unlocalised — `checkbox`, `date`, `formula`. It
 *   is shown beside a property the user cannot pick, so that "why is `Progress` not here?" has an
 *   answer on screen. A type this build has never heard of still names itself.
 */
data class NotionProperty(
    val id: String,
    val name: String,
    val typeName: String,
    val role: NotionPropertyRole,
)

/**
 * What a property is allowed to be in the mapping.
 *
 * **Deciding this in the data layer rather than the screen is the point.** `CLAUDE.md` §3.2 requires
 * the mapping UI to *prevent* a read-only property being chosen "rather than failing at sync time",
 * and prevention that depends on a `when` in a composable is prevention somebody can forget. By the
 * time a property reaches the UI it already knows which of these it is.
 */
enum class NotionPropertyRole {
    /**
     * A `date` property — eligible to be the day key.
     *
     * `docs/notion-schema.md` §2: the `Date` property is what says which day a page *is*. Not the
     * title, which this database leaves constant, and not `created_time`.
     */
    DayKey,

    /** A `checkbox` property — eligible to be a habit. In this database, habits *are* checkboxes. */
    Habit,

    /**
     * A `number` property — eligible to be a **measure**: pulled, averaged, never pushed (M15.4).
     *
     * ⚠ **`Weight` and `Sleep` used to be [Unavailable], and the KDoc below used to name them as
     * its examples.** They are why this case exists — the user asked for their daily numbers on the
     * Stats screen, *"read only, just the average on stats"*.
     *
     * ⭐ **The line between this and [Unavailable] is not writability.** Notion would let us write a
     * number; we do not, and cannot: [com.cristianprincipi.habits.data.remote.notion.mapper.NotionPropertyValue.Writable]
     * has exactly two implementations and `Number` is not one of them, so there is no method to
     * call. What the line actually is: whether the app has somewhere to put the value. A number it
     * has; a `multi_select` it has not.
     */
    Measure,

    /**
     * None of the three, and therefore not offered.
     *
     * Two different reasons land here and the user does not need them separated: a `formula` or a
     * `rollup` cannot be written at all, and a `multi_select` is perfectly writable but is not a
     * day, a habit or a number. `Area` and `Progress` are both this, and `docs/notion-schema.md`
     * §7 requires every one of them to come through a sync untouched.
     *
     * ⚠ **Mapping a measure does not weaken that §7 requirement for the two that moved out of
     * here.** A measure is read and never written, so `Weight` and `Sleep` come through a sync
     * exactly as untouched as they were when they were listed in this paragraph.
     */
    Unavailable,
}

/**
 * The mapping that is already saved, if setup has been through before.
 *
 * **What makes "pre-tick on first run only" possible.** A first run has nothing here and starts with
 * every checkbox ticked, which for a database whose checkboxes are all habits is a one-tap step. A
 * later run starts from exactly what was mapped before — so a column added in Notion since then
 * arrives **unticked and new**, which is the moment the user wants to be asked rather than assumed
 * at. Assuming would quietly turn a `Reviewed?` flag into a habit the app writes to.
 */
data class SavedNotionMapping(
    val sourceId: String,
    val dayKeyPropertyId: String,
    val habitPropertyIds: List<String>,
    /**
     * The mapped number columns and the unit typed for each. Empty for every mapping saved before
     * M15.4, hence the default — an old mapping is not a broken one, it is one with no measures.
     */
    val measures: List<SavedMeasure> = emptyList(),
)

/**
 * A number column the user has chosen to average, and what to call its unit.
 *
 * ⚠⚠ **[unit] is typed by the user and cannot be anything else.** `NotionPropertySchemaDto` is
 * `id, name, type` and carries no number format, and `CLAUDE.md` §3.4 forbids inferring "kg" from a
 * property *named* `Weight`. `docs/notion-schema.md` §6 has the real answer — kg and decimal hours,
 * confirmed by the owner — and **nothing in this codebase is allowed to know it**.
 *
 * Blank is normal and means "show the bare number". ⚠ It is the ambiguous case for `Sleep`
 * specifically: §2.4 records that `7.5` means 7h30m, which a unitless `7.5` does not say.
 */
data class SavedMeasure(
    val propertyId: String,
    val unit: String,
)

/**
 * What a step of the setup flow produced.
 *
 * **The failure is a [SyncFailure], deliberately reusing M4.11's set rather than inventing a second
 * one.** Every way connecting can fail is a way syncing can fail, said earlier: a rejected token, a
 * database that was never shared, no network. Two parallel vocabularies for the same problems would
 * mean two sets of sentences to write and keep in step, and the sync-status screen (M4.13) and this
 * one would disagree about what to call the same thing.
 */
sealed interface NotionSetupResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : NotionSetupResult<T>

    data class Failure(
        val reason: SyncFailure,
    ) : NotionSetupResult<Nothing>

    /**
     * The pasted link had nothing id-shaped in it, so **nothing was sent anywhere**.
     *
     * A separate outcome rather than a [SyncFailure], because every failure in that set is
     * something Notion said and this one never reached Notion. Folding it in would render the
     * sharing instruction — the app's most useful sentence, and completely the wrong one — at a
     * user who has simply pasted the wrong thing into the box.
     */
    data object UnreadableLink : NotionSetupResult<Nothing>
}
