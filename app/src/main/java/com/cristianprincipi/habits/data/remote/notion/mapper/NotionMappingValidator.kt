package com.cristianprincipi.habits.data.remote.notion.mapper

import com.cristianprincipi.habits.data.preferences.NotionDataSourceId
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
                when (val entry = schemaById[habitId.value]) {
                    null -> {
                        problems += NotionMappingProblem.HabitMissing(habitId)
                        null
                    }

                    else -> {
                        if (entry.propertyType == NotionPropertyType.Checkbox) {
                            MappedProperty(habitId, entry.name)
                        } else {
                            problems += NotionMappingProblem.HabitRetyped(habitId, entry.name, entry.type)
                            null
                        }
                    }
                }
            }

        // A checkbox in the schema that the mapping does not name is NOT a problem, and is not
        // adopted either. `docs/notion-schema.md` §4.3: `Weight`, `Sleep`, `Area` and `Progress`
        // share this schema and are not habits, so "new column" and "new habit" are not the same
        // event. Only the user can say which — M4.12's screen asks.
        return if (problems.isEmpty() && dayKey != null) {
            NotionMappingCheck.Valid(dayKey = dayKey, habits = habits)
        } else {
            NotionMappingCheck.Broken(problems)
        }
    }
}
