package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionSetupResult
import com.cristianprincipi.habits.domain.model.NotionSource
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
    ) {
        savedMappings += SavedNotionMapping(sourceId, dayKeyPropertyId, habitPropertyIds)
        configured.value = true
    }

    override suspend fun importNow(): NotionSetupResult<Int> = importResult

    /** What each disconnect asked for: `true` when the local data was to go too. */
    val disconnects = mutableListOf<Boolean>()

    override suspend fun disconnect(alsoDeleteLocalData: Boolean) {
        disconnects += alsoDeleteLocalData
        connected.value = false
        configured.value = false
    }
}
