package com.cristianprincipi.habits.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The mapping type's invariants — the states it refuses to be in.
 *
 * Everything here is about making a bad mapping unrepresentable rather than detectable, which is
 * the same choice ADR-044 made for writability. The one that earns its place is the duplicate
 * habit: ADR-035 found that Room's `@Upsert` answers a duplicate `notionPropertyId` by writing
 * nothing and throwing nothing, so a duplicate that reaches the database is invisible.
 */
class NotionPropertyMappingTest {
    // Real ids from docs/notion-schema.md §6.1, percent-encoded exactly as Notion sends them.
    private val readId = NotionPropertyId("ZI%40W")
    private val dateId = NotionPropertyId("M%3BBw")
    private val journalId = NotionPropertyId("Ye%3Aq")
    private val weightId = NotionPropertyId("%3FfMg")
    private val sleepId = NotionPropertyId("%7BQ%5Ct")

    private val dataSource = NotionDataSourceId("2f0b1c8e-0000-4000-8000-000000000000")

    @Test
    fun oneHabitCannotBeMappedTwice() {
        // Not an assertion about `Set`. It is the assertion ADR-035 asked for: the import in M4.8
        // builds one habit per entry, so if this collapsed anywhere later than here it would
        // collapse in a table that reports neither a row written nor an error.
        val mapping =
            NotionPropertyMapping(
                dataSourceId = dataSource,
                dayKeyPropertyId = dateId,
                habitPropertyIds = setOf(readId, readId),
            )

        assertEquals(listOf(readId), mapping.habitPropertyIds.toList())
    }

    @Test
    fun theHabitsKeepTheOrderTheUserChose() {
        // `HabitEntity.sortOrder` is assigned from this order, and the design offers no way to
        // reorder afterwards. A HashSet here would shuffle the user's habit list on every install.
        val mapping =
            NotionPropertyMapping(
                dataSourceId = dataSource,
                dayKeyPropertyId = dateId,
                habitPropertyIds = setOf(journalId, readId),
            )

        assertEquals(listOf(journalId, readId), mapping.habitPropertyIds.toList())
    }

    @Test
    fun theDayKeyCannotAlsoBeAHabit() {
        // A property is a date or a checkbox, never both, so this can only arrive from a bug — and
        // it would make every day's page write its own date as a completion.
        assertThrows(IllegalArgumentException::class.java) {
            NotionPropertyMapping(
                dataSourceId = dataSource,
                dayKeyPropertyId = dateId,
                habitPropertyIds = setOf(readId, dateId),
            )
        }
    }

    @Test
    fun aMappingWithoutADataSourceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            NotionPropertyMapping(
                dataSourceId = NotionDataSourceId(""),
                dayKeyPropertyId = dateId,
                habitPropertyIds = setOf(readId),
            )
        }
    }

    @Test
    fun aMappingWithoutADayKeyIsRejected() {
        // Without it there is no way to say which day a page is, so nothing can be read or written.
        assertThrows(IllegalArgumentException::class.java) {
            NotionPropertyMapping(
                dataSourceId = dataSource,
                dayKeyPropertyId = NotionPropertyId("  "),
                habitPropertyIds = setOf(readId),
            )
        }
    }

    @Test
    fun aBlankHabitIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            NotionPropertyMapping(
                dataSourceId = dataSource,
                dayKeyPropertyId = dateId,
                habitPropertyIds = setOf(readId, NotionPropertyId("")),
            )
        }
    }

    @Test
    fun aConnectionWithNoHabitsChosenYetIsAllowed() {
        // Setup collects the data source first and the habits second; a mapping with none is a
        // coherent half-finished connection, and "pick at least one" is a sentence M4.12's screen
        // can say. A type that forbade it would make that screen carry the state some other way.
        val mapping =
            NotionPropertyMapping(
                dataSourceId = dataSource,
                dayKeyPropertyId = dateId,
                habitPropertyIds = emptySet(),
            )

        assertEquals(emptySet<NotionPropertyId>(), mapping.habitPropertyIds)
    }

    // --- measures (M15.4) ---------------------------------------------------------------------

    @Test
    fun oneMeasureCannotBeMappedTwiceEvenWithDifferentUnits() {
        // ⭐⭐ **The discriminating case, and the reason this test exists at all.**
        // `habitPropertyIds` gets ADR-035's guarantee from being a `Set`. `measures` cannot: a
        // NotionMeasure carries a unit, so the two entries below are *distinct* set members and a
        // `Set<NotionMeasure>` would hold both quite happily. Written with different units on
        // purpose — the same-unit version would pass against a `Set` and prove nothing.
        assertThrows(IllegalArgumentException::class.java) {
            NotionPropertyMapping(
                dataSourceId = dataSource,
                dayKeyPropertyId = dateId,
                habitPropertyIds = setOf(readId),
                measures = listOf(NotionMeasure(weightId, "kg"), NotionMeasure(weightId, "lb")),
            )
        }
    }

    @Test
    fun aPropertyCannotBeBothAHabitAndAMeasure() {
        // A checkbox is not a number, so this can only arrive from a bug — and it would make one
        // Notion column both a check-off the app writes and a reading it averages.
        assertThrows(IllegalArgumentException::class.java) {
            NotionPropertyMapping(
                dataSourceId = dataSource,
                dayKeyPropertyId = dateId,
                habitPropertyIds = setOf(readId),
                measures = listOf(NotionMeasure(readId, "kg")),
            )
        }
    }

    @Test
    fun theDayKeyCannotAlsoBeAMeasure() {
        // [theDayKeyCannotAlsoBeAHabit]'s twin. Averaging a column of dates is not a smaller
        // mistake than checking one off.
        assertThrows(IllegalArgumentException::class.java) {
            NotionPropertyMapping(
                dataSourceId = dataSource,
                dayKeyPropertyId = dateId,
                habitPropertyIds = setOf(readId),
                measures = listOf(NotionMeasure(dateId, "")),
            )
        }
    }

    @Test
    fun aMeasureKeepsTheUnitAndTheOrderTheUserGaveIt() {
        // The order is the display order, exactly as it is for habits, and the unit is app-local
        // text that exists nowhere but here until M15.4b copies it into Room.
        val mapping =
            NotionPropertyMapping(
                dataSourceId = dataSource,
                dayKeyPropertyId = dateId,
                habitPropertyIds = setOf(readId),
                measures = listOf(NotionMeasure(sleepId, "h"), NotionMeasure(weightId, "kg")),
            )

        assertEquals(listOf(sleepId, weightId), mapping.measures.map(NotionMeasure::id))
        assertEquals(listOf("h", "kg"), mapping.measures.map(NotionMeasure::unit))
    }

    @Test
    fun aMeasureWithNoUnitIsAllowed() {
        // Blank means "show the bare number" and is the default state of the box on the mapping
        // screen. Requiring a unit would make the field mandatory for a purely cosmetic label.
        val mapping =
            NotionPropertyMapping(
                dataSourceId = dataSource,
                dayKeyPropertyId = dateId,
                habitPropertyIds = setOf(readId),
                measures = listOf(NotionMeasure(weightId, "")),
            )

        assertEquals("", mapping.measures.single().unit)
    }

    @Test
    fun aBlankMeasureIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            NotionMeasure(NotionPropertyId(" "), "kg")
        }
    }
}
