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

    override suspend fun loadProperties(sourceId: String): NotionSetupResult<List<NotionProperty>> = propertiesResult

    override suspend fun savedMapping(): SavedNotionMapping? = existingMapping

    override suspend fun saveMapping(
        sourceId: String,
        dayKeyPropertyId: String,
        habitPropertyIds: List<String>,
    ) {
        savedMappings += SavedNotionMapping(sourceId, dayKeyPropertyId, habitPropertyIds)
    }

    override suspend fun importNow(): NotionSetupResult<Int> = importResult
}
