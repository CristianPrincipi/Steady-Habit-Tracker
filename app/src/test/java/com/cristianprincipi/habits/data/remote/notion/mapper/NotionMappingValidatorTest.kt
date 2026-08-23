package com.cristianprincipi.habits.data.remote.notion.mapper

import com.cristianprincipi.habits.data.preferences.NotionDataSourceId
import com.cristianprincipi.habits.data.preferences.NotionPropertyId
import com.cristianprincipi.habits.data.preferences.NotionPropertyMapping
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDataSourceDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPropertySchemaDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The check that runs before every sync (`CLAUDE.md` §3.4).
 *
 * Names come from `docs/notion-schema.md` §2.3 in their verified form — including the `U+FE0F` that
 * only `Journal` carries — because these fixtures double as the record of what the API returns. Ids
 * are percent-encoded for the same reason.
 *
 * The test that matters most is [aRenamedHabitIsNotAProblem]: it is the whole payoff of keying on
 * the id (ADR-038), and the failure it rules out — a rename reading as a deletion — is the one
 * whose recovery would be "restore history" rather than "re-map".
 */
class NotionMappingValidatorTest {
    private val dataSourceId = "2f0b1c8e-0000-4000-8000-000000000000"

    private val readId = NotionPropertyId("ZI%40W")
    private val journalId = NotionPropertyId("Ye%3Aq")
    private val dateId = NotionPropertyId("M%3BBw")

    private val read = property(readId, "📖 Read", "checkbox")
    private val journal = property(journalId, "✒️ Journal", "checkbox")
    private val date = property(dateId, "Date", "date")

    private val mapping =
        NotionPropertyMapping(
            dataSourceId = NotionDataSourceId(dataSourceId),
            dayKeyPropertyId = dateId,
            habitPropertyIds = setOf(readId, journalId),
        )

    @Test
    fun aSchemaThatStillMatchesValidates() {
        val check = NotionMappingValidator.validate(mapping, schemaOf(read, journal, date))

        assertEquals(
            NotionMappingCheck.Valid(
                dayKey = MappedProperty(dateId, "Date"),
                habits =
                    listOf(
                        MappedProperty(readId, "📖 Read"),
                        MappedProperty(journalId, "✒️ Journal"),
                    ),
            ),
            check,
        )
    }

    @Test
    fun theHabitsComeBackInTheMappingsOrderNotTheSchemas() {
        // Notion's `properties` object has no order we may rely on; the user's choice does.
        val check = NotionMappingValidator.validate(mapping, schemaOf(date, journal, read))

        assertEquals(listOf(readId, journalId), check.valid().habits.map(MappedProperty::id))
    }

    @Test
    fun aRenamedHabitIsNotAProblem() {
        // ⭐ ADR-038. `Read` became `Reading` upstream; the id did not move, so this is a non-event
        // that hands the caller the new name to store.
        val renamed = property(readId, "📖 Reading", "checkbox")

        val check = NotionMappingValidator.validate(mapping, schemaOf(renamed, journal, date))

        val habit = check.valid().habits.first()

        assertEquals("📖 Reading", habit.name)
    }

    @Test
    fun aCheckboxTheUserDidNotMapIsLeftAlone() {
        // `Weight`, `Sleep`, `Area` and `Progress` share this schema and are not habits
        // (docs/notion-schema.md §4.3). A new column is not a new habit, so it is neither a problem
        // nor something to adopt — only M4.12's screen can ask.
        val area = property(NotionPropertyId("Yb%3EA"), "Area", "multi_select")
        val progress = property(NotionPropertyId("Xq%3Dp"), "Progress", "formula")

        val check = NotionMappingValidator.validate(mapping, schemaOf(read, journal, date, area, progress))

        assertEquals(listOf(readId, journalId), check.valid().habits.map(MappedProperty::id))
    }

    @Test
    fun aDeletedHabitPropertyPausesSync() {
        // Deleting a checkbox column in Notion destroys every value in it (§4.1), so the local
        // history may now be the only copy. Nothing here deletes anything in response.
        val check = NotionMappingValidator.validate(mapping, schemaOf(read, date))

        assertEquals(listOf(NotionMappingProblem.HabitMissing(journalId)), check.problems())
    }

    @Test
    fun aRetypedHabitPausesSyncAndNamesWhatItBecame() {
        // Writing `{"checkbox": false}` to a property that is now a select would overwrite whatever
        // the user meant by it — §3.4's "never overwrite a real value".
        val retyped = property(journalId, "✒️ Journal", "select")

        val check = NotionMappingValidator.validate(mapping, schemaOf(read, retyped, date))

        assertEquals(
            listOf(NotionMappingProblem.HabitRetyped(journalId, "✒️ Journal", "select")),
            check.problems(),
        )
    }

    @Test
    fun aTypeThisBuildHasNeverHeardOfIsStillNamed() {
        // `NotionPropertyType.fromWire` answers null for a type Notion added after this build, and
        // the problem carries the raw string so the user is told what it actually is.
        val retyped = property(readId, "📖 Read", "a_type_from_next_year")

        val check = NotionMappingValidator.validate(mapping, schemaOf(retyped, journal, date))

        assertEquals(
            listOf(NotionMappingProblem.HabitRetyped(readId, "📖 Read", "a_type_from_next_year")),
            check.problems(),
        )
    }

    @Test
    fun aDeletedDayKeyPausesSync() {
        // Without the date property there is no way to say which day a page is.
        val check = NotionMappingValidator.validate(mapping, schemaOf(read, journal))

        assertEquals(listOf(NotionMappingProblem.DayKeyMissing(dateId)), check.problems())
    }

    @Test
    fun aRetypedDayKeyPausesSync() {
        val retyped = property(dateId, "Date", "rich_text")

        val check = NotionMappingValidator.validate(mapping, schemaOf(read, journal, retyped))

        assertEquals(listOf(NotionMappingProblem.DayKeyRetyped(dateId, "Date", "rich_text")), check.problems())
    }

    @Test
    fun aSchemaFromSomeOtherDataSourceIsRejectedBeforeItsColumnsAreBelieved() {
        // Otherwise every mapped property would be reported deleted, and the user would be told to
        // re-map a database that never changed.
        val other = "9999abcd-0000-4000-8000-000000000000"

        val check = NotionMappingValidator.validate(mapping, schemaOf(read, journal, date, id = other))

        assertTrue(
            NotionMappingProblem.WrongDataSource(
                expected = NotionDataSourceId(dataSourceId),
                actual = NotionDataSourceId(other),
            ) in check.problems(),
        )
    }

    @Test
    fun everyProblemIsReportedTogether() {
        // Fixing one and being shown the next is how a person concludes the app is broken.
        val retyped = property(readId, "📖 Read", "select")

        val check = NotionMappingValidator.validate(mapping, schemaOf(retyped))

        assertEquals(
            listOf(
                NotionMappingProblem.DayKeyMissing(dateId),
                NotionMappingProblem.HabitRetyped(readId, "📖 Read", "select"),
                NotionMappingProblem.HabitMissing(journalId),
            ),
            check.problems(),
        )
    }

    @Test
    fun aPropertyIsFoundByItsIdAndNeverByTheMapKey() {
        // ⭐ The asymmetry M4.5 and M4.6 both flagged: `properties` is keyed by NAME. This schema is
        // what a stale client would produce — the key says one thing, the entry says another — and
        // the validator must not care about the key at all.
        val schema =
            NotionDataSourceDto(
                id = dataSourceId,
                properties =
                    mapOf(
                        "the key is not the name" to property(readId, "📖 Reading", "checkbox"),
                        "nor is this one" to journal,
                        "Date" to date,
                    ),
            )

        val check = NotionMappingValidator.validate(mapping, schema)

        val habit = check.valid().habits.first()

        assertEquals("📖 Reading", habit.name)
    }

    @Test
    fun aMappingWithNoHabitsValidatesAgainstItsDayKeyAlone() {
        // The half-finished connection of NotionPropertyMappingTest, seen from here: nothing to
        // sync is not the same as something wrong.
        val check = NotionMappingValidator.validate(mapping.copy(habitPropertyIds = emptySet()), schemaOf(date))

        assertEquals(emptyList<MappedProperty>(), check.valid().habits)
    }

    private fun property(
        id: NotionPropertyId,
        name: String,
        type: String,
    ) = NotionPropertySchemaDto(id = id.value, name = name, type = type)

    /** A schema keyed the way Notion keys one: by property **name**. */
    private fun schemaOf(
        vararg properties: NotionPropertySchemaDto,
        id: String = dataSourceId,
    ) = NotionDataSourceDto(id = id, properties = properties.associateBy(NotionPropertySchemaDto::name))

    private fun NotionMappingCheck.valid(): NotionMappingCheck.Valid =
        this as? NotionMappingCheck.Valid ?: error("Expected a valid mapping but got $this")

    private fun NotionMappingCheck.problems(): List<NotionMappingProblem> =
        (this as? NotionMappingCheck.Broken)?.problems ?: error("Expected a broken mapping but got $this")
}
