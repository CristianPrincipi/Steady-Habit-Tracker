package com.cristianprincipi.habits.data.remote.notion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Turning what a user can copy into an id the API will take.
 *
 * Every shape below is one a person can actually produce, and the rules they exercise are quoted in
 * `docs/notion-api.md` §16 from the live reference — none of it is remembered (`CLAUDE.md` §3.2).
 *
 * **No real workspace, database or view id appears here.** These are hex strings of the right shape.
 */
class NotionDatabaseLinkTest {
    private val id = "8f2c41d97b5e40a3ac6e1d9f0b73e254"
    private val dashed = "8f2c41d9-7b5e-40a3-ac6e-1d9f0b73e254"

    @Test
    fun aBareIdIsAlreadyAnAnswer() {
        assertEquals(id, NotionDatabaseLink.idOrNull(id))
    }

    @Test
    fun aDashedIdLosesItsDashes() {
        // Both forms are accepted by the API; normalising means only one shape is ever sent.
        assertEquals(id, NotionDatabaseLink.idOrNull(dashed))
    }

    @Test
    fun aPlainDatabaseUrlYieldsTheId() {
        assertEquals(id, NotionDatabaseLink.idOrNull("https://www.notion.so/$id"))
    }

    @Test
    fun aWorkspacePrefixIsIgnored() {
        assertEquals(id, NotionDatabaseLink.idOrNull("https://www.notion.so/myworkspace/$id"))
    }

    @Test
    fun aTitleSlugBeforeTheIdIsIgnored() {
        // The shape Notion actually puts in the address bar. The slug's own words are dash-separated,
        // which is why the match is taken from the end rather than the start.
        assertEquals(id, NotionDatabaseLink.idOrNull("https://www.notion.so/myworkspace/My-Habits-$id"))
    }

    @Test
    fun aSlugWordThatLooksLikeHexDoesNotWin() {
        // "decafbad" and friends are legal English-ish slug words and legal hex. Taking the last
        // match is what stops one being mistaken for the id.
        assertEquals(id, NotionDatabaseLink.idOrNull("https://www.notion.so/decafbad-Habits-$id"))
    }

    @Test
    fun theViewIdInTheQueryStringIsNotTheDatabase() {
        // ⭐ The trap. A database URL carries ?v=<32 hex> — the VIEW id, same shape, wrong thing.
        // The reference draws the boundary at the question mark, and this is why.
        val viewId = "0123456789abcdef0123456789abcdef"

        assertEquals(id, NotionDatabaseLink.idOrNull("https://www.notion.so/My-Habits-$id?v=$viewId"))
    }

    @Test
    fun aFragmentIsDroppedToo() {
        val blockId = "0123456789abcdef0123456789abcdef"

        assertEquals(id, NotionDatabaseLink.idOrNull("https://www.notion.so/My-Habits-$id#$blockId"))
    }

    @Test
    fun aTrailingSlashChangesNothing() {
        assertEquals(id, NotionDatabaseLink.idOrNull("https://www.notion.so/$id/"))
    }

    @Test
    fun surroundingWhitespaceFromAClumsyPasteIsFine() {
        assertEquals(id, NotionDatabaseLink.idOrNull("  https://www.notion.so/$id  "))
    }

    @Test
    fun anUppercaseIdIsStillAnId() {
        // Notion sends lowercase, but a user retyping one has no reason to know that.
        assertEquals(id.uppercase(), NotionDatabaseLink.idOrNull(id.uppercase()))
    }

    @Test
    fun nothingIdShapedMeansNoAnswer() {
        assertNull(NotionDatabaseLink.idOrNull(""))
        assertNull(NotionDatabaseLink.idOrNull("https://www.notion.so/My-Habits"))
        assertNull(NotionDatabaseLink.idOrNull("my habits database"))
    }

    @Test
    fun aStringOneCharacterShortIsNotAnId() {
        // The guard against a loose "32-ish hex characters" match handing the API something it will
        // reject as invalid — a worse error to show than "that does not look like a link".
        assertNull(NotionDatabaseLink.idOrNull(id.dropLast(1)))
    }

    @Test
    fun aTokenIsNotADatabaseLink() {
        // The other thing on the user's clipboard during setup, and pasting it in the wrong field is
        // an easy mistake. It must not resolve to something that gets sent anywhere.
        assertNull(NotionDatabaseLink.idOrNull("ntn_notarealtokenjustastringofletters"))
    }
}
