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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.filmix.core.model.StreamLink
import net.filmix.core.model.WatchProgress

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
    fun toWatchProgress() = WatchProgress(positionMs, durationMs, updatedAt)

    /** Treated as finished near the end, so it is not offered as "resume". */
    val isEffectivelyFinished: Boolean
        get() = toWatchProgress().isFinished
}

@Dao
interface ResumeDao {
    @Query("SELECT * FROM resume_positions WHERE streamKey = :key LIMIT 1")
    suspend fun find(key: String): ResumePosition?

    @Query("SELECT * FROM resume_positions WHERE postId = :postId")
    fun observeForPost(postId: Int): Flow<List<ResumePosition>>

    @Query("SELECT * FROM resume_positions WHERE postId = :postId")
    suspend fun forPost(postId: Int): List<ResumePosition>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: ResumePosition)

    @Query("DELETE FROM resume_positions WHERE streamKey = :key")
    suspend fun delete(key: String)
}

@Database(entities = [ResumePosition::class], version = 2, exportSchema = true)
abstract class FilmixDatabase : RoomDatabase() {
    abstract fun resumeDao(): ResumeDao
}

class ResumeStore(context: Context) {

    private val db = Room
        .databaseBuilder(context.applicationContext, FilmixDatabase::class.java, "filmix.db")
        .addMigrations(REKEY_LEGACY_STREAM_KEYS)
        .fallbackToDestructiveMigration()
        .build()

    private val dao = db.resumeDao()

    /**
     * Every stored row for a post, keyed by stream key — including the finished
     * rows [resumeFor] deliberately hides, because "which episodes are done" is
     * exactly the question here. Room's invalidation tracker re-emits after the
     * player saves, so watched marks update on return with no explicit reload.
     * Invalidation is table-level, so unchanged row sets are dropped before the
     * map is rebuilt.
     */
    fun progressForPost(postId: Int): Flow<Map<String, WatchProgress>> =
        dao.observeForPost(postId)
            .distinctUntilChanged()
            .map { rows -> rows.associate { it.streamKey to it.toWatchProgress() } }

    /** One-shot form of [progressForPost], for reads that must not observe. */
    suspend fun progressSnapshotForPost(postId: Int): Map<String, WatchProgress> =
        withContext(Dispatchers.IO) {
            dao.forPost(postId).associate { it.streamKey to it.toWatchProgress() }
        }

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

        /**
         * v1 keyed rows by the full stream URL, whose signed `/s/<token>/`
         * segment rotates between fetches — which orphaned every row on the
         * next refetch of its post. v2 keys on the stable tail, and the new
         * key is computable from the old one, so rewrite instead of
         * discarding. Only URL-shaped keys are legacy: rows written by the
         * first stable-tail builds under schema v1 are already in the new
         * form and must not be re-shortened.
         */
        val REKEY_LEGACY_STREAM_KEYS = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val renames = mutableListOf<Pair<String, String>>()
                db.query("SELECT streamKey FROM resume_positions").use { cursor ->
                    while (cursor.moveToNext()) {
                        val old = cursor.getString(0)
                        if (!old.contains("://")) continue
                        val new = StreamLink.resumeKey(old)
                        if (new != old) renames += old to new
                    }
                }
                for ((old, new) in renames) {
                    // When rows exist under both keys, keep the freshest:
                    // drop the legacy row if the new-keyed one is newer,
                    // otherwise drop the new-keyed row and take over its key.
                    db.execSQL(
                        "DELETE FROM resume_positions WHERE streamKey = ? AND updatedAt < " +
                            "(SELECT updatedAt FROM resume_positions WHERE streamKey = ?)",
                        arrayOf(old, new),
                    )
                    db.execSQL(
                        "DELETE FROM resume_positions WHERE streamKey = ? AND EXISTS " +
                            "(SELECT 1 FROM resume_positions WHERE streamKey = ?)",
                        arrayOf(new, old),
                    )
                    db.execSQL(
                        "UPDATE resume_positions SET streamKey = ? WHERE streamKey = ?",
                        arrayOf(new, old),
                    )
                }
            }
        }
    }
}
