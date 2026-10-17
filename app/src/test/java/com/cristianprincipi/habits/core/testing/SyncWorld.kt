package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.data.local.TestTransactionRunner
import com.cristianprincipi.habits.data.local.dao.FakeCompletionDao
import com.cristianprincipi.habits.data.local.dao.FakeDayDao
import com.cristianprincipi.habits.data.local.dao.FakeHabitDao
import com.cristianprincipi.habits.data.local.dao.FakeMeasureDao
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.HabitCompletionEntity
import com.cristianprincipi.habits.data.local.entity.MeasurementEditEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.preferences.NotionDataSourceId
import com.cristianprincipi.habits.data.preferences.NotionMappingStore
import com.cristianprincipi.habits.data.preferences.NotionMeasure
import com.cristianprincipi.habits.data.preferences.NotionPropertyId
import com.cristianprincipi.habits.data.preferences.NotionPropertyMapping
import com.cristianprincipi.habits.data.preferences.SettingsDataStore
import com.cristianprincipi.habits.data.preferences.readablePropertyIds
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDataSourceDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPageDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionParentDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPropertySchemaDto
import com.cristianprincipi.habits.data.remote.notion.mapper.NotionPropertyValue
import com.cristianprincipi.habits.data.remote.notion.mapper.property
import com.cristianprincipi.habits.data.repository.MeasureRepositoryImpl
import com.cristianprincipi.habits.data.repository.SettingsRepositoryImpl
import com.cristianprincipi.habits.data.sync.DiscardedEditStore
import com.cristianprincipi.habits.data.sync.HabitImporter
import com.cristianprincipi.habits.data.sync.MeasureImporter
import com.cristianprincipi.habits.data.sync.PullFormatStore
import com.cristianprincipi.habits.data.sync.SyncEngine
import com.cristianprincipi.habits.domain.model.MeasureId
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The property ids from `docs/notion-schema.md` §6.1, percent-encoded exactly as Notion sends them.
 *
 * Tidy ids would let a bug through: anything that decoded, trimmed or split on a delimiter would
 * pass every test written with `read-1` and then fail against the user's real database.
 */
object PropertyIds {
    const val DATA_SOURCE = "ds-1"
    const val READ = "ZI%40W"
    const val JOURNAL = "Ye%3Aq"
    const val DATE = "M%3BBw"

    /** `Weight` — the user's own daily data, deliberately never in the mapping. */
    const val WEIGHT = "Nw%7Cb"

    /**
     * The database that *contains* [DATA_SOURCE] — what a `notion.so` link is built from.
     *
     * Hex of the right shape, and no real workspace id (`NotionDatabaseLinkTest`'s rule). It is a
     * different string from [DATA_SOURCE] on purpose: the two ids are not interchangeable, and a
     * fixture that reused one would let a mix-up pass.
     */
    const val DATABASE = "8f2c41d97b5e40a3ac6e1d9f0b73e254"
}

/**
 * The user's schema, near enough: two habits, the day key, and one column that is not a habit.
 *
 * Names are the verified ones from `docs/notion-schema.md` §2.3, including the `U+FE0F` that only
 * `Journal` carries, because these fixtures double as the record of what the API returns.
 */
val defaultSchema: NotionDataSourceDto =
    dataSourceOf(
        NotionPropertySchemaDto(PropertyIds.READ, "📖 Read", "checkbox"),
        NotionPropertySchemaDto(PropertyIds.JOURNAL, "✒️ Journal", "checkbox"),
        NotionPropertySchemaDto(PropertyIds.DATE, "Date", "date"),
        NotionPropertySchemaDto(PropertyIds.WEIGHT, "Weight", "number"),
    )

val defaultMapping: NotionPropertyMapping =
    NotionPropertyMapping(
        dataSourceId = NotionDataSourceId(PropertyIds.DATA_SOURCE),
        dayKeyPropertyId = NotionPropertyId(PropertyIds.DATE),
        habitPropertyIds = setOf(NotionPropertyId(PropertyIds.READ), NotionPropertyId(PropertyIds.JOURNAL)),
    )

/** Fixed, so a sync is as reproducible next year as today (`CLAUDE.md` §6 — no clock is ever read). */
val syncTestClock: Clock = Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC)

val august19: LocalDate = LocalDate.of(2026, 8, 19)
val august20: LocalDate = LocalDate.of(2026, 8, 20)

/**
 * A data source keyed the way Notion keys one: by property **name**.
 *
 * **It carries its database parent by default, because a real one always does.** M7.5 reads that
 * parent to build the link out to Notion, and a fixture without it would let every sync test pass
 * while the real response was being parsed wrongly. Pass `databaseId = null` for the one case that
 * has no database parent — an externally synced source.
 */
fun dataSourceOf(
    vararg properties: NotionPropertySchemaDto,
    id: String = PropertyIds.DATA_SOURCE,
    databaseId: String? = PropertyIds.DATABASE,
): NotionDataSourceDto =
    NotionDataSourceDto(
        id = id,
        properties = properties.associateBy(NotionPropertySchemaDto::name),
        parent = databaseId?.let { NotionParentDto(type = "database_id", databaseId = it) },
    )

/**
 * A page, keyed by property **name**, with the id inside the value.
 *
 * Nothing in the engine may read that key — the mapping is on ids (ADR-038) — and building the
 * fixtures faithfully is what would catch it if something did.
 */
fun page(
    id: String,
    date: LocalDate,
    checks: Map<String, Boolean> = emptyMap(),
    editedAtMillis: Long = 1_000,
    gone: Boolean = false,
): NotionPageDto =
    NotionPageDto(
        id = id,
        lastEditedTime = Instant.ofEpochMilli(editedAtMillis).toString(),
        properties =
            buildMap {
                put("Date", dateProperty(date))
                checks.forEach { (propertyId, checked) ->
                    put(nameOf(propertyId), checkboxProperty(propertyId, checked))
                }
            },
        inTrash = gone,
    )

/**
 * The same page, re-keyed with names this app has never seen. **The ids inside are untouched.**
 *
 * Notion keys a page's `properties` by **name**, and the user can rename a column at any moment
 * without telling us — so between one pull and the next, every key on every page can change while
 * nothing about the data does. `NotionPageDto.rawProperty` exists precisely because of that, and
 * [page] alone cannot catch a regression in it: its keys always come from the schema the test also
 * gave the app, so a name-keyed lookup would still find them and pass.
 *
 * These keys match nothing at all. Anything that reads by key gets `null` and the test fails
 * loudly, which is what a mistake here would deserve: reading a renamed habit as an absent one
 * looks exactly like a deleted habit, and the recovery is "restore history" rather than "re-map"
 * (`docs/notion-schema.md` §6.1).
 */
fun NotionPageDto.keyedByUnknownNames(): NotionPageDto =
    copy(properties = properties.values.mapIndexed { index, value -> "renamed-in-notion-$index" to value }.toMap())

/** The user's `Weight` column: real data on the same page, which no sync may touch (§7). */
fun NotionPageDto.withWeight(weight: Double): NotionPageDto =
    copy(
        properties =
            properties +
                (
                    "Weight" to
                        buildJsonObject {
                            put("id", PropertyIds.WEIGHT)
                            put("type", "number")
                            put("number", weight)
                        }
                ),
    )

/**
 * The `Weight` column present on the page and **holding nothing** — a skipped weigh-in.
 *
 * ⚠⚠ Notion sends `"number": null` for this, which decodes to `NotionPropertyValue.Empty`, and
 * it is the one input in M15.4 that can be got wrong invisibly: read as `0.0` it stores a
 * zero-kilo day and drags the average down with nothing on screen looking unwell. Written out here
 * rather than produced by omitting the property, because an **absent** column is a different case
 * (the page predates it) and a fixture that conflated the two could not tell them apart.
 */
fun NotionPageDto.withEmptyWeight(): NotionPageDto =
    copy(
        properties =
            properties +
                (
                    "Weight" to
                        buildJsonObject {
                            put("id", PropertyIds.WEIGHT)
                            put("type", "number")
                            put("number", JsonNull)
                        }
                ),
    )

/** `Weight` retyped upstream to something this build will not read as a number. */
fun NotionPageDto.withUnreadableWeight(): NotionPageDto =
    copy(
        properties =
            properties +
                (
                    "Weight" to
                        buildJsonObject {
                            put("id", PropertyIds.WEIGHT)
                            put("type", "a_type_from_the_future")
                            put("a_type_from_the_future", "84")
                        }
                ),
    )

/** [defaultMapping] with `Weight` averaged — M15.4's addition, in kilograms. */
val mappingWithWeight: NotionPropertyMapping =
    defaultMapping.copy(measures = listOf(NotionMeasure(NotionPropertyId(PropertyIds.WEIGHT), "kg")))

/**
 * A number as Notion holds it on this page: the value, [NotionPropertyValue.Empty] when the column
 * is there and holds nothing, or `null` when the page has no such property at all.
 *
 * Read through the production decoder, by id — which works on a page this app wrote as well as on
 * one a test built, since `FakeNotionApi` returns written properties the way Notion does.
 */
fun NotionPageDto.numberAt(propertyId: String = PropertyIds.WEIGHT): NotionPropertyValue? = property(propertyId)

fun NotionPageDto.weightValue(): Double? =
    properties.values
        .firstOrNull { it["id"]?.jsonPrimitive?.content == PropertyIds.WEIGHT }
        ?.get("number")
        ?.jsonPrimitive
        ?.content
        ?.toDouble()

private fun nameOf(propertyId: String): String =
    defaultSchema.properties.values
        .single { it.id == propertyId }
        .name

private fun checkboxProperty(
    propertyId: String,
    checked: Boolean,
): JsonObject =
    buildJsonObject {
        put("id", propertyId)
        put("type", "checkbox")
        put("checkbox", checked)
    }

/** The day key's value, for a test that hand-builds a page around it. */
fun dateProperty(date: LocalDate): JsonObject =
    buildJsonObject {
        put("id", PropertyIds.DATE)
        put("type", "date")
        putJsonObject("date") { put("start", date.toString()) }
    }

/**
 * One app's worth of local storage, plus the Notion workspace it syncs against.
 *
 * Wires itself rather than being handed its parts, so a test reads as a story about the two sides
 * drifting apart. The one piece of wiring worth noticing is the pair of lambdas between the day and
 * completion fakes: they reproduce the foreign key and its cascade, which is what the engine leans
 * on when a page turns out to be gone.
 */
class SyncWorld(
    schema: NotionDataSourceDto = defaultSchema,
    clock: Clock = syncTestClock,
    /**
     * Whether this world stands for an install that **upgraded across a pull-format change** and
     * therefore owes a backfill (ADR-132).
     *
     * ⚠ **Default false, which is "an install that is already up to date"**, and that is the honest
     * default rather than a convenience: every test here is about incremental sync, and a world that
     * silently owed a backfill would make the second run of each one re-read everything — quietly
     * turning assertions about the watermark into assertions about nothing.
     */
    private val upgradedInstall: Boolean = false,
) {
    val api = FakeNotionApi(schema)
    val habits = FakeHabitDao()
    val days = FakeDayDao()
    val completions = FakeCompletionDao { epochDay -> epochDay in days.current }
    val measures = FakeMeasureDao(dayExists = { epochDay -> epochDay in days.current })

    private val preferences = InMemoryDataStore()
    val mappingStore = NotionMappingStore(preferences)
    val settings = SettingsDataStore(preferences)
    val discardedEdits = DiscardedEditStore(preferences)
    val pullFormat = PullFormatStore(preferences)

    /** The phone's clock for a number typed on it — moved per edit by [setNumberLocally]. */
    private val deviceClock = MutableClock(Instant.ofEpochMilli(DEFAULT_LOCAL_EDIT_MILLIS))

    /**
     * ⭐ The production write path for a number (M16.2), not a copy of it. [checkOffLocally]
     * re-states `HabitRepositoryImpl.setCompleted`; a number's write decides its base value and
     * whether anything is owed at all, and a re-statement that drifted from the real one would let
     * every sync test here pass against a write the app does not make.
     */
    val numbers =
        MeasureRepositoryImpl(
            measureDao = measures,
            dayDao = days,
            transactionRunner = TestTransactionRunner(),
            clock = deviceClock,
            widgets = RecordingWidgetRefresher(),
        )

    val engine =
        SyncEngine(
            api = api,
            mappingStore = mappingStore,
            habitImporter =
                HabitImporter(
                    habitDao = habits,
                    dayDao = days,
                    completionDao = completions,
                    transactionRunner = TestTransactionRunner(),
                    clock = clock,
                ),
            measureImporter = MeasureImporter(measureDao = measures),
            dayDao = days,
            completionDao = completions,
            measureDao = measures,
            transactionRunner = TestTransactionRunner(),
            discardedEdits = discardedEdits,
            pullFormat = pullFormat,
            settings = SettingsRepositoryImpl(settings),
            clock = clock,
        )

    init {
        days.onDelete = { epochDay ->
            completions.dropDay(epochDay)
            // The second cascade `measurements` declares. Missing it here would let a JVM test
            // watch a reading outlive the day it belongs to, which the device would not.
            measures.dropDay(epochDay)
        }
    }

    suspend fun connect(mapping: NotionPropertyMapping = defaultMapping) {
        mappingStore.save(mapping)
        // ⚠⚠ Recorded HERE and not in `init`, because a backfill is owed against a *mapping* and
        // there is none until now (ADR-132). Without this every world would owe one, and the second
        // `sync()` of every test would silently re-read everything — turning each assertion about
        // the watermark into an assertion about nothing.
        if (!upgradedInstall) pullFormat.recordBackfill(mapping.readablePropertyIds())
    }

    /**
     * A local check-off, written exactly as `HabitRepositoryImpl.setCompleted` writes one: the day
     * first, then the completion, and `PENDING_PUSH` stamped whatever changed.
     */
    suspend fun checkOffLocally(
        date: LocalDate,
        propertyId: String,
        at: Long = 5_000,
    ) {
        val habitId = habits.current.single { it.notionPropertyId == propertyId }.id
        val epochDay = date.toEpochDay()
        val existing = days.find(epochDay)
        days.upsert(
            existing?.copy(localUpdatedAtEpochMillis = at, syncState = SyncState.PENDING_PUSH)
                ?: DayEntity(
                    epochDay = epochDay,
                    notionPageId = null,
                    localUpdatedAtEpochMillis = at,
                    remoteLastEditedAtEpochMillis = null,
                    syncState = SyncState.PENDING_PUSH,
                ),
        )
        completions.insert(HabitCompletionEntity(habitId = habitId, epochDay = epochDay))
    }

    /**
     * A number typed on the phone, through [numbers]. `null` clears it.
     *
     * @param at the device's clock at the moment of typing — the local side of last-write-wins.
     */
    suspend fun setNumberLocally(
        date: LocalDate,
        value: Double?,
        at: Long = DEFAULT_LOCAL_EDIT_MILLIS,
        propertyId: String = PropertyIds.WEIGHT,
    ) {
        deviceClock.setTo(Instant.ofEpochMilli(at))
        numbers.setReading(MeasureId(measureId(propertyId)), date, value)
    }

    /** The local id of the measure backing a Notion number column. */
    fun measureId(propertyId: String = PropertyIds.WEIGHT): String =
        measures.current.single { it.notionPropertyId == propertyId }.id

    /** The reading Room holds for a day, or `null` for none. */
    fun readingOn(
        date: LocalDate,
        propertyId: String = PropertyIds.WEIGHT,
    ): Double? =
        measures.currentReadings
            .singleOrNull { it.measureId == measureId(propertyId) && it.epochDay == date.toEpochDay() }
            ?.value

    /** What this device still owes Notion for a number on a day, or `null`. */
    fun owedOn(
        date: LocalDate,
        propertyId: String = PropertyIds.WEIGHT,
    ): MeasurementEditEntity? =
        measures.currentEdits.singleOrNull { it.measureId == measureId(propertyId) && it.epochDay == date.toEpochDay() }

    /** The local id of the habit backing a Notion property. */
    fun habitId(propertyId: String): String = habits.current.single { it.notionPropertyId == propertyId }.id

    fun dayAt(date: LocalDate): DayEntity = days.current.getValue(date.toEpochDay())

    private companion object {
        /** [checkOffLocally]'s default too: after every fixture page's stamp unless a test says otherwise. */
        const val DEFAULT_LOCAL_EDIT_MILLIS = 5_000L
    }
}

/** A connected world, ready to sync. The state every test but the first-run ones starts from. */
suspend fun connectedWorld(schema: NotionDataSourceDto = defaultSchema): SyncWorld =
    SyncWorld(schema).apply { connect() }
