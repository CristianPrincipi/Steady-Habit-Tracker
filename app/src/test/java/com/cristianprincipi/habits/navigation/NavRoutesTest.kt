package com.cristianprincipi.habits.navigation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * That every route is serializable, which is the one thing about these declarations that can break
 * without breaking the build.
 *
 * Navigation Compose builds a destination's route pattern from its `KSerializer`. A route that lost
 * its `@Serializable` still compiles everywhere it is *referenced* — the failure surfaces at
 * runtime, when someone opens that screen. These tests are the compile-time cost of that mistake.
 */
class NavRoutesTest {
    private val json = Json

    @Test
    fun everyTopLevelRouteRoundTrips() {
        assertEquals(Month, json.decodeFromString<Month>(json.encodeToString(Month)))
        assertEquals(Week, json.decodeFromString<Week>(json.encodeToString(Week)))
        assertEquals(Year, json.decodeFromString<Year>(json.encodeToString(Year)))
        assertEquals(Stats, json.decodeFromString<Stats>(json.encodeToString(Stats)))
        assertEquals(Settings, json.decodeFromString<Settings>(json.encodeToString(Settings)))
    }

    @Test
    fun manageHabitsRoundTripsTooEvenThoughItIsNotATab() {
        assertEquals(ManageHabits, json.decodeFromString<ManageHabits>(json.encodeToString(ManageHabits)))
    }

    @Test
    fun noRouteCarriesArguments() {
        // ADR-010 left nothing to address by id, and an argument added without a reason is an
        // invitation to build the screen that would need it. A data object has no elements; the day
        // one legitimately does, this test is the place that says so out loud.
        listOf(
            Month.serializer(),
            Week.serializer(),
            Year.serializer(),
            Stats.serializer(),
            Settings.serializer(),
            ManageHabits.serializer(),
        ).forEach { serializer ->
            assertEquals(serializer.descriptor.serialName, 0, serializer.descriptor.elementsCount)
        }
    }
}
