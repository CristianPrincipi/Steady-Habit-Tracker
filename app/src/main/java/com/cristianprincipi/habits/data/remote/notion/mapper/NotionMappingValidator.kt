package com.cristianprincipi.habits.data.remote.notion.mapper

import com.cristianprincipi.habits.data.preferences.NotionDataSourceId
import com.cristianprincipi.habits.data.preferences.NotionPropertyId
import com.cristianprincipi.habits.data.preferences.NotionPropertyMapping
import com.cristianprincipi.habits.data.remote.notion.dto.NotionDataSourceDto
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPropertySchemaDto

/**
 * Checks a saved [NotionPropertyMapping] against a freshly fetched schema.
 *
 * **This runs before every sync**, not only at setup — `CLAUDE.md` §3.4. The user's database is
 * theirs to change at any moment, and the failure this prevents is the quiet one: a lookup that
 * misses, a habit that reads as never completed, a write that lands on a column that now means
 * something else.
 *
 * **It lives in `data/remote` because it reads a DTO**, and `CLAUDE.md` §6 says wire types never
 * leave that package. The mapping itself lives in `data/preferences` where §5's tree puts it, and
 * nothing in [NotionMappingCheck] is a DTO — so the answer travels outward while
 * [NotionDataSourceDto] stays here.
 */
object NotionMappingValidator {
    /**
     * @param schema the data source as Notion has it now — `NotionApi.retrieveDataSource`.
     */
    fun validate(
        mapping: NotionPropertyMapping,
        schema: NotionDataSourceDto,
    ): NotionMappingCheck {
        val problems = mutableListOf<NotionMappingProblem>()

        if (schema.id != mapping.dataSourceId.value) {
            problems +=
                NotionMappingProblem.WrongDataSource(
                    expected = mapping.dataSourceId,
                    actual = NotionDataSourceId(schema.id),
                )
        }

        // ⭐ Indexed by the id INSIDE each entry, never by the map key. The key is the property
        // NAME (`NotionDataSourceDto.properties`), and a name-keyed lookup is precisely what
        // ADR-038 chose the id to avoid: a renamed habit would come back as a deleted one.
        val schemaById = schema.properties.values.associateBy(NotionPropertySchemaDto::id)

        val dayKey =
            when (val entry = schemaById[mapping.dayKeyPropertyId.value]) {
                null -> {
                    problems += NotionMappingProblem.DayKeyMissing(mapping.dayKeyPropertyId)
                    null
                }

                else -> {
                    if (entry.propertyType == NotionPropertyType.Date) {
                        MappedProperty(mapping.dayKeyPropertyId, entry.name)
                    } else {
                        problems +=
                            NotionMappingProblem.DayKeyRetyped(mapping.dayKeyPropertyId, entry.name, entry.type)
                        null
                    }
                }
            }

        val habits =
            mapping.habitPropertyIds.mapNotNull { habitId ->
                schemaById.resolve(
                    id = habitId,
                    required = NotionPropertyType.Checkbox,
                    onMissing = NotionMappingProblem::HabitMissing,
                    onRetyped = NotionMappingProblem::HabitRetyped,
                    problems = problems,
                )
            }

        // ⭐ The same call, and that is the argument for extracting it: a measure fails in exactly
        // the two ways a habit does — missing or retyped — and for exactly §3.4's reason. An
        // average taken over a column that is no longer there is not a smaller number, it is a
        // wrong one, and nothing on the Stats screen would look unwell while it was being drawn.
        val measures =
            mapping.measures.mapNotNull { measure ->
                schemaById
                    .resolve(
                        id = measure.id,
                        required = NotionPropertyType.Number,
                        onMissing = NotionMappingProblem::MeasureMissing,
                        onRetyped = NotionMappingProblem::MeasureRetyped,
                        problems = problems,
                    )?.let { MappedMeasure(it, measure.unit) }
            }

        // A checkbox — or a number — in the schema that the mapping does not name is NOT a problem,
        // and is not adopted either. `docs/notion-schema.md` §4.3: `Area` and `Progress` share this
        // schema and are neither, so "new column" and "new habit" are not the same event. Only the
        // user can say which — M4.12's screen asks. ⚠ That argument now covers numbers too, and it
        // is why `withMapping` never pre-ticks a measure the way it pre-ticks a first run's habits.
        return if (problems.isEmpty() && dayKey != null) {
            NotionMappingCheck.Valid(dayKey = dayKey, habits = habits, measures = measures)
        } else {
            NotionMappingCheck.Broken(problems)
        }
    }

    /**
     * One mapped id, looked up and type-checked, appending a problem when it is neither.
     *
     * ⚠ **Indexed by the id inside each entry, never by the map key**, and the receiver is already
     * built that way — see [validate]. Extracting this does not re-open that question; it only
     * stops the habit loop and the measure loop stating the same three-case decision twice.
     *
     * @return the resolved property, or `null` — in which case [problems] has grown by one.
     */
    private fun Map<String, NotionPropertySchemaDto>.resolve(
        id: NotionPropertyId,
        required: NotionPropertyType,
        onMissing: (NotionPropertyId) -> NotionMappingProblem,
        onRetyped: (NotionPropertyId, String, String) -> NotionMappingProblem,
        problems: MutableList<NotionMappingProblem>,
    ): MappedProperty? {
        val entry = this[id.value]
        return when {
            entry == null -> {
                problems += onMissing(id)
                null
            }

            entry.propertyType == required -> {
                MappedProperty(id, entry.name)
            }

            else -> {
                problems += onRetyped(id, entry.name, entry.type)
                null
            }
        }
    }
}
