package net.filmix.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.filmix.core.model.StreamLink

/**
 * Resume positions, keyed by the quality-independent form of the stream URL so
 * a position saved at 480p is still found when the same title is later played
 * at 1080p. The reference app does the same normalisation before writing to its
 * `viewed_video` table.
 */
@Entity(tableName = "resume_positions")
data class ResumePosition(
    @PrimaryKey val streamKey: String,
    val postId: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    /** Treated as finished near the end, so it is not offered as "resume". */
    val isEffectivelyFinished: Boolean
        get() = durationMs > 0 && positionMs > durationMs - FINISHED_TAIL_MS

    private companion object {
        const val FINISHED_TAIL_MS = 60_000L
    }
}

@Dao
interface ResumeDao {
    @Query("SELECT * FROM resume_positions WHERE streamKey = :key LIMIT 1")
    suspend fun find(key: String): ResumePosition?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: ResumePosition)

    @Query("DELETE FROM resume_positions WHERE streamKey = :key")
    suspend fun delete(key: String)
}

@Database(entities = [ResumePosition::class], version = 1, exportSchema = true)
abstract class FilmixDatabase : RoomDatabase() {
    abstract fun resumeDao(): ResumeDao
}

class ResumeStore(context: Context) {

    private val db = Room
        .databaseBuilder(context.applicationContext, FilmixDatabase::class.java, "filmix.db")
        .fallbackToDestructiveMigration()
        .build()

    private val dao = db.resumeDao()

    /** Null when nothing is stored, or when the stored position is at the end. */
    suspend fun resumeFor(streamUrl: String): Long? = withContext(Dispatchers.IO) {
        dao.find(StreamLink.resumeKey(streamUrl))
            ?.takeIf { !it.isEffectivelyFinished && it.positionMs > MIN_RESUME_MS }
            ?.positionMs
    }

    suspend fun save(streamUrl: String, postId: Int, positionMs: Long, durationMs: Long) {
        withContext(Dispatchers.IO) {
            val key = StreamLink.resumeKey(streamUrl)
            // Below the threshold there is nothing worth resuming; clear instead
            // of storing a position that would jump the user to the very start.
            if (positionMs <= MIN_RESUME_MS) {
                dao.delete(key)
                return@withContext
            }
            dao.upsert(
                ResumePosition(
                    streamKey = key,
                    postId = postId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private companion object {
        const val MIN_RESUME_MS = 15_000L
    }
}
