package com.cristianprincipi.habits.core.testing

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * A deliberately trivial database that exists ONLY to prove the Room toolchain works.
 *
 * **This is not the app's schema and must never become it.** The real entities arrive in M3 and
 * live in `data/local/entity`. This one is a test fixture: it gives [createInMemoryDatabase]
 * something concrete to build, and — more importantly — it forces Room's KSP processor to actually
 * generate code. ADR-014 could only prove KSP works via Hilt, because no `@Entity` existed yet.
 *
 * `exportSchema = false` because a probe has no migration history worth committing. The real
 * database exports its schema, as `CLAUDE.md` §6 requires.
 */
@Entity(tableName = "probe")
data class ProbeEntity(
    @PrimaryKey val id: Long,
    val label: String,
)

@Dao
interface ProbeDao {
    @Insert
    suspend fun insert(probe: ProbeEntity)

    @Query("SELECT * FROM probe WHERE id = :id")
    suspend fun findById(id: Long): ProbeEntity?
}

@Database(entities = [ProbeEntity::class], version = 1, exportSchema = false)
abstract class RoomToolchainProbeDatabase : RoomDatabase() {
    abstract fun probeDao(): ProbeDao
}
