package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.data.preferences.NotionDataSourceId
import com.cristianprincipi.habits.data.preferences.NotionPropertyId
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.remote.notion.mapper.NotionMappingProblem
import com.cristianprincipi.habits.domain.model.MappingIssue
import com.cristianprincipi.habits.domain.model.SyncFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * The translation `CLAUDE.md` §6 requires: nothing that speaks HTTP escapes the data layer.
 *
 * The wire errors split on **what the server said**; [SyncFailure] splits on **what the user can do
 * about it**, so this is deliberately not one-to-one. Each test below is one of those groupings,
 * and the grouping is the thing worth pinning — a later session that quietly re-splits a case would
 * be changing what a screen says.
 */
class SyncOutcomeFailureTest {
    private fun failing(error: NotionError) = SyncOutcome.Failed(error)

    @Test
    fun aRunThatWorkedHasNothingToSay() {
        assertNull(SyncOutcome.Success(daysPulled = 3, daysPushed = 1).failure)
    }

    @Test
    fun anAppThatWasNeverConnectedHasNotFailedAtAnything() {
        // A fresh install, or a disconnect. The setup screen is the answer, not a red banner.
        assertNull(SyncOutcome.NotConfigured.failure)
    }

    @Test
    fun everyWayTheTokenCanBeTheProblemKeepsItsOwnAnswer() {
        // Three different fixes: connect, issue a new token, edit the connection's capabilities.
        assertEquals(SyncFailure.NotConnected, failing(NotionError.NotConnected).failure)
        assertEquals(SyncFailure.TokenRejected, failing(NotionError.Unauthorized).failure)
        assertEquals(SyncFailure.MissingCapability, failing(NotionError.Forbidden).failure)
    }

    @Test
    fun aFourOhFourIsNamedForTheCaseItUsuallyIs() {
        // CLAUDE.md §3.3: Notion cannot tell "gone" from "never shared", and "not found" sends the
        // user hunting for a typo that is not there.
        assertEquals(SyncFailure.DatabaseNotShared, failing(NotionError.MissingOrNotShared).failure)
    }

    @Test
    fun beingThrottledAndNotionBeingDownAreTheSameSentence() {
        // Both mean wait; the worker's own backoff is already doing it.
        assertEquals(SyncFailure.NotionUnavailable, failing(NotionError.RateLimited(30.seconds)).failure)
        assertEquals(SyncFailure.NotionUnavailable, failing(NotionError.Server(status = 503)).failure)
    }

    @Test
    fun aDeadSocketIsTheUsersConnectionRatherThanNotions() {
        assertEquals(SyncFailure.Offline, failing(NotionError.Network(IOException("airplane mode"))).failure)
    }

    @Test
    fun bothWaysOfBeingWrongAboutTheApiReadAsTheSameThing() {
        // A request Notion rejected and a response that would not parse are both this build being
        // wrong about the API (docs/notion-api.md §11) — nothing the user can act on.
        assertEquals(SyncFailure.AppOutOfDate, failing(NotionError.Invalid("validation_error", "nope")).failure)
        assertEquals(SyncFailure.AppOutOfDate, failing(NotionError.Malformed(IllegalStateException())).failure)
    }

    @Test
    fun theNotionCodeNeverReachesTheAnswer() {
        // The point of the whole translation: the message Notion sent is not a thing to render.
        val failure = failing(NotionError.Invalid(code = "validation_error", message = "body.properties invalid"))

        assertEquals(SyncFailure.AppOutOfDate, failure.failure)
    }

    @Test
    fun everyMappingProblemCrossesTheBoundaryWithWhatItKnows() {
        // The value classes stop at the data layer; a property id is an opaque string above it.
        val problems =
            listOf(
                NotionMappingProblem.DayKeyMissing(NotionPropertyId("M%3BBw")),
                NotionMappingProblem.DayKeyRetyped(NotionPropertyId("M%3BBw"), "Date", "rich_text"),
                NotionMappingProblem.HabitMissing(NotionPropertyId("ZI%40W")),
                NotionMappingProblem.HabitRetyped(NotionPropertyId("Ye%3Aq"), "✒️ Journal", "number"),
                NotionMappingProblem.WrongDataSource(NotionDataSourceId("ds-1"), NotionDataSourceId("ds-2")),
            )

        val failure = SyncOutcome.MappingBroken(problems).failure

        assertEquals(
            SyncFailure.MappingBroken(
                listOf(
                    MappingIssue.DayKeyMissing("M%3BBw"),
                    MappingIssue.DayKeyRetyped("M%3BBw", "Date", "rich_text"),
                    MappingIssue.HabitMissing("ZI%40W"),
                    MappingIssue.HabitRetyped("Ye%3Aq", "✒️ Journal", "number"),
                    MappingIssue.WrongDataSource,
                ),
            ),
            failure,
        )
    }

    @Test
    fun everyProblemSurvivesRatherThanTheFirstOne() {
        // CLAUDE.md §3.4: a user who renamed one column and deleted another is told both at once.
        val problems =
            listOf(
                NotionMappingProblem.HabitMissing(NotionPropertyId("a")),
                NotionMappingProblem.HabitMissing(NotionPropertyId("b")),
                NotionMappingProblem.HabitMissing(NotionPropertyId("c")),
            )

        val failure = SyncOutcome.MappingBroken(problems).failure as SyncFailure.MappingBroken

        assertEquals(3, failure.issues.size)
    }
}
