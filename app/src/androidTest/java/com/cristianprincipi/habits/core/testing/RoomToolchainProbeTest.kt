package com.cristianprincipi.habits.core.testing

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves Room actually reads and writes through [createInMemoryDatabase]. See
 * [RoomToolchainProbeDatabase] for why a probe schema exists at all.
 */
@RunWith(AndroidJUnit4::class)
class RoomToolchainProbeTest {
    private val database = createInMemoryDatabase<RoomToolchainProbeDatabase>()
    private val dao = database.probeDao()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertedRowIsReadBack() =
        runTest {
            dao.insert(ProbeEntity(id = 1L, label = "checked"))

            assertEquals("checked", dao.findById(1L)?.label)
        }

    @Test
    fun missingRowReturnsNull() =
        runTest {
            assertNull(dao.findById(404L))
        }
}
