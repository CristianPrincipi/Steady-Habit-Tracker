package com.cristianprincipi.habits.data.remote.notion.mapper

import com.cristianprincipi.habits.data.remote.notion.dto.NotionPropertySchemaDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The property-type table.
 *
 * Its whole value is that the strings and the read-only flags are right, so that is what these
 * check — against the list transcribed from the live reference in M4.6, not against the enum's own
 * opinion of itself.
 */
class NotionPropertyTypeTest {
    /**
     * The types the reference states cannot be updated through the API.
     *
     * Written out here rather than derived from the enum on purpose: a test that asked the enum
     * which types it thinks are read-only would agree with any answer it gave.
     */
    private val documentedReadOnly =
        setOf("formula", "rollup", "created_by", "created_time", "last_edited_by", "last_edited_time", "unique_id")

    @Test
    fun theReadOnlySetIsExactlyWhatTheDocsSayItIs() {
        // CLAUDE.md §3.2: read-only types are pull-only BY DEFINITION, and the mapping UI has to
        // prevent one being chosen as a write target rather than letting it fail at sync time.
        // Everything downstream of that requirement reads this flag.
        val flagged =
            NotionPropertyType.entries
                .filter(
                    NotionPropertyType::isReadOnly,
                ).map(NotionPropertyType::wireName)

        assertEquals(documentedReadOnly, flagged.toSet())
    }

    @Test
    fun theSixTypesThisDatabaseActuallyHasAreAllPresent() {
        // docs/notion-schema.md §2: a Title, five Checkboxes, a Date, a Formula, a Multi-select and
        // two Numbers. If any of these went missing the mapping screen could not describe the
        // user's own database.
        listOf("title", "checkbox", "date", "formula", "multi_select", "number").forEach { wireName ->
            assertTrue(wireName, NotionPropertyType.fromWire(wireName) != null)
        }
    }

    @Test
    fun theTwoThingsV1WritesAreNotReadOnly() {
        assertFalse(NotionPropertyType.Checkbox.isReadOnly)
        assertFalse(NotionPropertyType.Date.isReadOnly)
    }

    @Test
    fun progressIsPullOnlyByDefinition() {
        // docs/notion-schema.md §2.2: Progress is a formula, so the app computes its own and must
        // never write this one. Notion recomputes it from the checkboxes we do write.
        assertTrue(NotionPropertyType.Formula.isReadOnly)
    }

    @Test
    fun everyWireNameRoundTrips() {
        NotionPropertyType.entries.forEach { type ->
            assertEquals(type, NotionPropertyType.fromWire(type.wireName))
        }
    }

    @Test
    fun wireNamesAreUnique() {
        val names = NotionPropertyType.entries.map(NotionPropertyType::wireName)

        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun aTypeThisBuildHasNeverHeardOfIsNullRatherThanAFailure() {
        // Notion adds property types. An unrecognised one has to stay a value the mapping screen
        // can name and skip — NotionPropertySchemaDto keeps the raw string for exactly that.
        assertNull(NotionPropertyType.fromWire("a_type_notion_added_later"))
        assertNull(NotionPropertyType.fromWire(""))
    }

    @Test
    fun aSchemaEntryReportsItsType() {
        val checkbox = NotionPropertySchemaDto(id = "ZI%40W", name = "📖 Read", type = "checkbox")
        val future = NotionPropertySchemaDto(id = "abc", name = "Whatever", type = "a_type_notion_added_later")

        assertEquals(NotionPropertyType.Checkbox, checkbox.propertyType)
        assertNull(future.propertyType)
        // The raw string survives, so the UI can still show the user what it is.
        assertEquals("a_type_notion_added_later", future.type)
    }
}
