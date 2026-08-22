package com.cristianprincipi.habits.data.remote.notion.mapper

import com.cristianprincipi.habits.data.remote.notion.NotionJson
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPageDto
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The property codec, against the shapes the live reference documents and the values
 * `docs/notion-schema.md` says the user's database actually holds.
 *
 * Fixtures use the **real** property names and ids from that document, including the emoji, because
 * §2.3 records them for exactly this purpose. Two of the five emoji were misidentified from a
 * screenshot once already; a test that used approximations would not have caught it.
 */
class NotionPropertyCodecTest {
    private fun decode(json: String) = NotionPropertyCodec.decode(NotionJson.parseToJsonElement(json) as JsonObject)

    // The ids from docs/notion-schema.md §6.1 — percent-encoded exactly as Notion sends them.
    private val readId = "ZI%40W"
    private val dateId = "M%3BBw"

    @Test
    fun aCheckedHabitIsACheckbox() {
        assertEquals(
            NotionPropertyValue.Checkbox(checked = true),
            decode("""{"id":"$readId","type":"checkbox","checkbox":true}"""),
        )
    }

    @Test
    fun anUncheckedHabitIsACheckboxToo() {
        // Not Empty. A checkbox is never null in Notion, and "false" is a real answer that the
        // streak calculation depends on being different from "no page for that day".
        assertEquals(
            NotionPropertyValue.Checkbox(checked = false),
            decode("""{"id":"$readId","type":"checkbox","checkbox":false}"""),
        )
    }

    @Test
    fun aCheckboxThatIsNotABooleanIsUnreadable() {
        assertEquals(
            NotionPropertyValue.Unreadable("checkbox"),
            decode("""{"id":"$readId","type":"checkbox","checkbox":"yes"}"""),
        )
    }

    @Test
    fun theDayKeyIsADateOnlyValue() {
        // docs/notion-schema.md §2.4: ISO date, null time zone, no time component. This is the
        // shape every page in the user's database actually has.
        assertEquals(
            NotionPropertyValue.DateValue(LocalDate.of(2026, 8, 19)),
            decode("""{"id":"$dateId","type":"date","date":{"start":"2026-08-19","end":null,"time_zone":null}}"""),
        )
    }

    @Test
    fun aDateCarryingATimeIsTakenAsTheDayItWasWritten() {
        // The property has no time today. If the user ever turns one on, every page gains a
        // timestamp — and that must not be the day sync breaks.
        assertEquals(
            NotionPropertyValue.DateValue(LocalDate.of(2026, 8, 19)),
            decode("""{"type":"date","date":{"start":"2026-08-19T23:30:00.000-05:00"}}"""),
        )
    }

    @Test
    fun aLateEveningDateIsNotDraggedIntoTheNextDayByItsOffset() {
        // ⭐ CLAUDE.md §6: "a habit checked at 23:00 belongs to that day regardless of timezone".
        // 23:30 at -05:00 is already tomorrow in UTC. Converting would move the day; we do not
        // convert, we read the date as written.
        val value = decode("""{"type":"date","date":{"start":"2026-08-19T23:30:00.000-05:00"}}""")

        assertEquals(LocalDate.of(2026, 8, 19), (value as NotionPropertyValue.DateValue).start)
    }

    @Test
    fun aDateInUtcParsesToo() {
        assertEquals(
            NotionPropertyValue.DateValue(LocalDate.of(2020, 12, 8)),
            decode("""{"type":"date","date":{"start":"2020-12-08T12:00:00Z"}}"""),
        )
    }

    @Test
    fun aDateRangeKeepsItsEnd() {
        assertEquals(
            NotionPropertyValue.DateValue(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 21)),
            decode("""{"type":"date","date":{"start":"2026-08-19","end":"2026-08-21"}}"""),
        )
    }

    @Test
    fun anUndatedPropertyIsEmpty() {
        assertEquals(NotionPropertyValue.Empty, decode("""{"type":"date","date":null}"""))
    }

    @Test
    fun aDateThatWillNotParseIsUnreadableRatherThanEmpty() {
        // The distinction matters to the sync engine: empty is a page the user never dated,
        // unreadable is a page whose date we could not understand. Collapsing the second into the
        // first would silently drop a day that does exist.
        assertEquals(NotionPropertyValue.Unreadable("date"), decode("""{"type":"date","date":{"start":"yesterday"}}"""))
    }

    @Test
    fun aTitleIsFlattenedAcrossItsRuns() {
        // Notion splits rich text on formatting, so one visible word can arrive as several runs.
        val json =
            """
            {"type":"title","title":[
              {"type":"text","text":{"content":"Hab"},"plain_text":"Hab"},
              {"type":"text","text":{"content":"its"},"plain_text":"its"}
            ]}
            """.trimIndent()

        assertEquals(NotionPropertyValue.Text("Habits"), decode(json))
    }

    @Test
    fun anEmptyTitleIsEmptyText() {
        assertEquals(NotionPropertyValue.Text(""), decode("""{"type":"title","title":[]}"""))
    }

    @Test
    fun aNumberIsReadFromTheJsonNotTheDisplayString() {
        // docs/notion-schema.md §2.4: Weight EXPORTS as "84.00" and the API sends 84. Parsing the
        // formatted string would be parsing a localised, lossy rendering of the value.
        assertEquals(NotionPropertyValue.Number(84.0), decode("""{"type":"number","number":84}"""))
    }

    @Test
    fun anUnsetNumberIsEmpty() {
        assertEquals(NotionPropertyValue.Empty, decode("""{"type":"number","number":null}"""))
    }

    @Test
    fun aMultiSelectHoldingOneOptionIsStillAList() {
        // docs/notion-schema.md §2.4: Area held a single value in the export, and the API returns
        // an array regardless. Reading it as a scalar would work until the day it did not.
        assertEquals(
            NotionPropertyValue.MultiSelect(listOf("IT Security")),
            decode("""{"type":"multi_select","multi_select":[{"id":"tC;=","name":"IT Security","color":"purple"}]}"""),
        )
    }

    @Test
    fun aMultiSelectHoldingNothingIsAnEmptyList() {
        assertEquals(
            NotionPropertyValue.MultiSelect(emptyList()),
            decode("""{"type":"multi_select","multi_select":[]}"""),
        )
    }

    @Test
    fun aFormulaCarriesItsComputedResult() {
        // Progress: 3 of 5 habits done renders 60%, and the API sends the fraction.
        assertEquals(
            NotionPropertyValue.Formula(NotionPropertyValue.Number(0.6)),
            decode("""{"id":"CSoE","type":"formula","formula":{"type":"number","number":0.6}}"""),
        )
    }

    @Test
    fun everyFormulaResultKindDecodes() {
        assertEquals(
            NotionPropertyValue.Formula(NotionPropertyValue.Checkbox(true)),
            decode("""{"type":"formula","formula":{"type":"boolean","boolean":true}}"""),
        )
        assertEquals(
            NotionPropertyValue.Formula(NotionPropertyValue.Text("on track")),
            decode("""{"type":"formula","formula":{"type":"string","string":"on track"}}"""),
        )
        assertEquals(
            NotionPropertyValue.Formula(NotionPropertyValue.DateValue(LocalDate.of(2026, 8, 19))),
            decode("""{"type":"formula","formula":{"type":"date","date":{"start":"2026-08-19"}}}"""),
        )
        assertEquals(
            NotionPropertyValue.Formula(NotionPropertyValue.Unreadable("unsupported")),
            decode("""{"type":"formula","formula":{"type":"unsupported"}}"""),
        )
    }

    @Test
    fun aTypeThisBuildDoesNotDecodeIsUnreadableAndNamesItself() {
        // Not an exception, and not silence. The mapping screen can say "Tags (rollup) — cannot be
        // used as a habit" only because the type survives the decode.
        assertEquals(
            NotionPropertyValue.Unreadable("rollup"),
            decode("""{"type":"rollup","rollup":{"type":"number","number":2,"function":"count"}}"""),
        )
        assertEquals(
            NotionPropertyValue.Unreadable("a_type_notion_added_later"),
            decode("""{"type":"a_type_notion_added_later","whatever":{}}"""),
        )
    }

    @Test
    fun aPropertyWithNoTypeAtAllIsUnreadable() {
        assertEquals(NotionPropertyValue.Unreadable(""), decode("""{"id":"$readId","checkbox":true}"""))
    }

    @Test
    fun aCheckboxWritesTheShapeNotionExpects() {
        assertEquals(
            """{"checkbox":true}""",
            NotionJson.encodeToString(JsonObject.serializer(), NotionPropertyValue.Checkbox(true).toWire()),
        )
    }

    @Test
    fun aDateWritesItsStartAndOmitsAnAbsentEnd() {
        // Omitted, not null. A null `end` is Notion's instruction to clear one, and this app never
        // edits a range it did not create.
        assertEquals(
            """{"date":{"start":"2026-08-19"}}""",
            NotionJson.encodeToString(
                JsonObject.serializer(),
                NotionPropertyValue.DateValue(LocalDate.of(2026, 8, 19)).toWire(),
            ),
        )
    }

    @Test
    fun aDateRangeWritesBothEnds() {
        assertEquals(
            """{"date":{"start":"2026-08-19","end":"2026-08-21"}}""",
            NotionJson.encodeToString(
                JsonObject.serializer(),
                NotionPropertyValue.DateValue(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 21)).toWire(),
            ),
        )
    }

    @Test
    fun onlyTheTwoThingsThisAppOwnsCanBeWritten() {
        // CLAUDE.md §3.2 wants a read-only property PREVENTED from being a write target rather than
        // failing at sync time, and docs/notion-schema.md §7 wants Weight, Sleep and Area never
        // nulled out. Both hold because there is no way to ask the others for a request body.
        //
        // The list is typed as the supertype deliberately. Written as `Formula(…) is Writable` the
        // compiler folds each check to a constant and warns that it is always false — which proves
        // the property at compile time but leaves a test that can no longer fail. Filtering a
        // heterogeneous list is a real runtime check, and it breaks if a case is ever added to
        // Writable by accident.
        val everyKind: List<NotionPropertyValue> =
            listOf(
                NotionPropertyValue.Checkbox(true),
                NotionPropertyValue.DateValue(LocalDate.of(2026, 8, 19)),
                NotionPropertyValue.Text("Habits"),
                NotionPropertyValue.Number(84.0),
                NotionPropertyValue.MultiSelect(listOf("Study")),
                NotionPropertyValue.Formula(NotionPropertyValue.Number(0.6)),
                NotionPropertyValue.Empty,
                NotionPropertyValue.Unreadable("rollup"),
            )

        assertEquals(
            listOf(NotionPropertyValue.Checkbox(true), NotionPropertyValue.DateValue(LocalDate.of(2026, 8, 19))),
            everyKind.filterIsInstance<NotionPropertyValue.Writable>(),
        )
    }

    @Test
    fun aPropertyIsFoundByItsIdAndNotByTheNameItIsFiledUnder() {
        // ⭐ The asymmetry that makes this layer easy to get wrong: the map key is the property
        // NAME, the id is inside the value, and the mapping is keyed on the id (ADR-038). Here the
        // habit has been renamed in Notion since the mapping was saved — which is precisely the
        // case a name-keyed lookup would report as a deleted habit.
        val page = page(""""📖 Reading": {"id": "$readId", "type": "checkbox", "checkbox": true}""")

        assertEquals(NotionPropertyValue.Checkbox(true), page.property(readId))
    }

    @Test
    fun aPropertyMissingFromThePageIsNull() {
        // A page written before the habit column existed. Not an error, and not `false` either —
        // "no answer" and "not done" are different days.
        val page = page(""""📖 Read": {"id": "$readId", "type": "checkbox", "checkbox": true}""")

        assertNull(page.property(dateId))
    }

    @Test
    fun theRawPropertyIsAvailableForAnythingTheCodecDoesNotModel() {
        val page = page(""""Area": {"id": "abc", "type": "multi_select", "multi_select": []}""")

        assertEquals(
            "multi_select",
            page
                .rawProperty("abc")
                ?.get("type")
                ?.toString()
                ?.trim('"'),
        )
        assertNull(page.rawProperty("nope"))
    }

    private fun page(properties: String): NotionPageDto =
        NotionJson.decodeFromString(
            NotionPageDto.serializer(),
            """{"id":"page-1","last_edited_time":"2026-08-22T09:00:00.000Z","properties":{$properties}}""",
        )
}
