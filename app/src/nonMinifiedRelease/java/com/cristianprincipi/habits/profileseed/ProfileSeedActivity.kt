package com.cristianprincipi.habits.profileseed

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.room.withTransaction
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.data.local.HabitsDatabase
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.HabitCompletionEntity
import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.preferences.NotionDataSourceId
import com.cristianprincipi.habits.data.preferences.NotionMappingStore
import com.cristianprincipi.habits.data.preferences.NotionPropertyId
import com.cristianprincipi.habits.data.preferences.NotionPropertyMapping
import com.cristianprincipi.habits.data.remote.notion.auth.NotionTokenStore
import com.cristianprincipi.habits.domain.model.Habit
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import javax.inject.Inject

/**
 * Makes a fresh install look connected, so the Baseline Profile records the launch the user has.
 *
 * ### Why the generator cannot simply open the app
 *
 * `isConfigured` is "a mapping is stored", and a fresh install has none, so a cold start lands on the
 * **setup wizard**. The user sees that screen once. Every launch after it goes to Week — the tab
 * bar, Room's observed queries, the pills — and a profile recorded against the wizard would carry
 * rules for none of it. ADR-143 §2.
 *
 * ### ⚠⚠ Why it cannot reach Notion
 *
 * It writes a mapping and **no token**. `KtorNotionApi.exchange` reads the token first and returns
 * `NotionError.NotConnected` when there is none, before any request is built, so the sync that runs
 * on app open fails locally and silently. The ids below are invented and name nothing in any
 * workspace.
 *
 * ### It refuses when a token is stored
 *
 * A stored token means a real connection, and seeding beside one would put sample habits into the
 * user's Room and let the generator's check-off queue a write against their workspace — the harm
 * `SeedActivity` in `src/debug` guards against in the same way. The generator is locked to an emulator
 * three ways (`baselineprofile/build.gradle.kts`), so this should be unreachable; it is here so that
 * "should" is not the only thing standing there.
 *
 * ### Synchronous, on purpose
 *
 * The generator starts this with `am start -W` and kills the process straight afterwards to take a
 * cold start. `-W` returns once `onCreate` has returned, so the seed has to be *finished* by then — a
 * coroutine launched into `lifecycleScope` would be killed half-written. The work itself still runs
 * on Room's and DataStore's own dispatchers; only this thread waits.
 */
@AndroidEntryPoint
class ProfileSeedActivity : ComponentActivity() {
    @Inject
    lateinit var database: HabitsDatabase

    @Inject
    lateinit var mappingStore: NotionMappingStore

    @Inject
    lateinit var tokenStore: NotionTokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runBlocking {
            when {
                tokenStore.read() != null -> Log.w(TAG, "A Notion token is stored; not seeding a real connection.")
                mappingStore.read() != null -> Unit
                else -> seed(LocalDate.now())
            }
        }
        finish()
    }

    private suspend fun seed(today: LocalDate) {
        val habits = SampleHabits.habits(today)
        val completions = SampleHabits.completions(today)

        database.withTransaction {
            database.habitDao().upsert(habits.mapIndexed { index, habit -> habit.toRow(index) })
            completions.map { it.date }.distinct().forEach { date -> database.dayDao().upsert(dayRow(date)) }
            completions.forEach { completion ->
                database.completionDao().insert(
                    HabitCompletionEntity(completion.habitId.value, completion.date.toEpochDay()),
                )
            }
        }

        // Last, because this is the write that flips `isConfigured`: a kill before it leaves a
        // fresh install that simply seeds again, never a "connected" app with no habits.
        mappingStore.save(
            NotionPropertyMapping(
                dataSourceId = NotionDataSourceId(SEED_ID),
                dayKeyPropertyId = NotionPropertyId("$SEED_ID-day"),
                habitPropertyIds = habits.map { NotionPropertyId(it.propertyId()) }.toSet(),
            ),
        )
    }

    private fun Habit.propertyId() = "$SEED_ID-${id.value}"

    private fun Habit.toRow(index: Int) =
        HabitEntity(
            id = id.value,
            notionPropertyId = propertyId(),
            notionPropertyName = "$emoji $name",
            color = color.name,
            startDateEpochDay = startDate.toEpochDay(),
            isActive = true,
            sortOrder = index,
        )

    /** `SYNCED`: history the app is pretending it imported, so nothing is owed upstream. */
    private fun dayRow(date: LocalDate) =
        DayEntity(
            epochDay = date.toEpochDay(),
            notionPageId = null,
            localUpdatedAtEpochMillis = null,
            remoteLastEditedAtEpochMillis = null,
            syncState = SyncState.SYNCED,
        )

    private companion object {
        const val TAG = "ProfileSeed"
        const val SEED_ID = "profile-seed"
    }
}
