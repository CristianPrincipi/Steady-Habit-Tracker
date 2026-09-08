package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionSetupResult
import com.cristianprincipi.habits.domain.model.NotionSource
import com.cristianprincipi.habits.domain.model.SavedMeasure
import com.cristianprincipi.habits.domain.model.SavedNotionMapping
import com.cristianprincipi.habits.domain.repository.NotionSetupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [NotionSetupRepository] that answers from fields, so a ViewModel test is about the ViewModel.
 *
 * Every method has a settable result rather than behaviour: what the wizard does with a failure is
 * the thing under test, and `NotionSetupRepositoryImplTest` already covers how a failure is arrived
 * at.
 *
 * **The token is recorded, not kept.** [savedTokens] exists so a test can assert it was passed on
 * and trimmed — never so anything reads one back (`CLAUDE.md` §3.6).
 */
class FakeNotionSetupRepository : NotionSetupRepository {
    private val connected = MutableStateFlow(false)

    override val isConnected: Flow<Boolean> = connected

    /**
     * Puts the fake in the state a **relaunch** finds: a token was stored by an earlier run, and
     * nothing else was.
     *
     * ⚠ It sets no token string, because there is none to set — [saveToken] records what it was
     * given and the store only ever reports *whether* one exists (`CLAUDE.md` §3.6). That is
     * precisely the situation M13.7 is about: the app knows it has a token and cannot show it to
     * anybody, so a wizard that asks for it again is asking for something unrecoverable.
     */
    fun pretendTokenAlreadyStored() {
        connected.value = true
    }

    private val configured = MutableStateFlow(false)

    override val isConfigured: Flow<Boolean> = configured

    /** What the app has learned about where the database lives. Settable, like every other answer. */
    val notionDatabaseUrl = MutableStateFlow<String?>(null)

    override val databaseUrl: Flow<String?> = notionDatabaseUrl

    val savedTokens = mutableListOf<String>()
    val savedMappings = mutableListOf<SavedNotionMapping>()

    var sourcesResult: NotionSetupResult<List<NotionSource>> = NotionSetupResult.Success(emptyList())
    var linkResult: NotionSetupResult<List<NotionSource>> = NotionSetupResult.Success(emptyList())
    var propertiesResult: NotionSetupResult<List<NotionProperty>> = NotionSetupResult.Success(emptyList())
    var importResult: NotionSetupResult<Int> = NotionSetupResult.Success(0)

    /** What a previous run of setup left behind. `null` is a first run. */
    var existingMapping: SavedNotionMapping? = null

    override suspend fun saveToken(token: String) {
        savedTokens += token
        connected.value = true
    }

    override suspend fun findSources(): NotionSetupResult<List<NotionSource>> = sourcesResult

    override suspend fun findSourcesInDatabase(databaseLink: String): NotionSetupResult<List<NotionSource>> = linkResult

    /** Which data source each schema read asked for. M7.8 asserts it is the connected one. */
    val loadedSources = mutableListOf<String>()

    override suspend fun loadProperties(sourceId: String): NotionSetupResult<List<NotionProperty>> {
        loadedSources += sourceId
        return propertiesResult
    }

    override suspend fun savedMapping(): SavedNotionMapping? = existingMapping

    override suspend fun saveMapping(
        sourceId: String,
        dayKeyPropertyId: String,
        habitPropertyIds: List<String>,
        measures: List<SavedMeasure>,
    ) {
        savedMappings += SavedNotionMapping(sourceId, dayKeyPropertyId, habitPropertyIds, measures)
        configured.value = true
    }

    override suspend fun importNow(): NotionSetupResult<Int> = importResult

    /** What each disconnect asked for: `true` when the local data was to go too. */
    val disconnects = mutableListOf<Boolean>()

    /**
     * ⭐ **A device that will not let the token be removed — M13.12.**
     *
     * `CLAUDE.md` §3.6 requires disconnect to wipe the stored token, and before M13.12 a failure
     * here crashed the app. The attempt is still recorded, because a test about a **failed**
     * disconnect needs to prove one was tried.
     */
    var failDisconnectWith: Exception? = null

    override suspend fun disconnect(alsoDeleteLocalData: Boolean) {
        disconnects += alsoDeleteLocalData
        failDisconnectWith?.let { throw it }
        connected.value = false
        configured.value = false
    }
}
