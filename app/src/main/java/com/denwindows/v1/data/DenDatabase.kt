package com.denwindows.v1.data

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
import kotlinx.coroutines.flow.Flow

enum class DesktopItemKind {
    INTERNAL_APP,
    ANDROID_APP,
    FOLDER
}

@Entity(tableName = "desktop_items")
data class DesktopItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String, // DesktopItemKind.name
    val target: String, // DesktopAppType.name, Android packageName, or folder path
    val gridCol: Int,
    val gridRow: Int,
    val isPinnedToTaskbar: Boolean = false,
    val isPinnedToStart: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "browser_bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "browser_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateIso: String, // yyyy-MM-dd
    val title: String,
    val timeLabel: String,
    val notes: String = ""
)

@Entity(tableName = "recent_apps")
data class RecentAppEntity(
    @PrimaryKey val key: String, // "INTERNAL:BROWSER" or "ANDROID:com.android.chrome"
    val title: String,
    val kind: String,
    val target: String,
    val lastLaunchedAt: Long = System.currentTimeMillis()
)

@Dao
interface DesktopItemDao {
    @Query("SELECT * FROM desktop_items ORDER BY sortOrder ASC, gridCol ASC, gridRow ASC")
    fun observeAll(): Flow<List<DesktopItemEntity>>

    @Query("SELECT COUNT(*) FROM desktop_items")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DesktopItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DesktopItemEntity>)

    @Query("UPDATE desktop_items SET gridCol = :col, gridRow = :row WHERE id = :id")
    suspend fun updatePosition(id: String, col: Int, row: Int)

    @Query("UPDATE desktop_items SET isPinnedToTaskbar = :pinned WHERE id = :id")
    suspend fun setPinnedToTaskbar(id: String, pinned: Boolean)

    @Query("UPDATE desktop_items SET isPinnedToStart = :pinned WHERE id = :id")
    suspend fun setPinnedToStart(id: String, pinned: Boolean)

    @Query("DELETE FROM desktop_items WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface BrowserDao {
    @Query("SELECT * FROM browser_bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM browser_bookmarks WHERE url = :url")
    suspend fun removeBookmarkByUrl(url: String)

    @Query("SELECT * FROM browser_history ORDER BY visitedAt DESC LIMIT 200")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun addHistory(entry: HistoryEntity)

    @Query("DELETE FROM browser_history")
    suspend fun clearHistory()
}

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_events ORDER BY dateIso ASC, timeLabel ASC")
    fun observeAllEvents(): Flow<List<CalendarEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEvent(id: Long)
}

@Dao
interface RecentAppDao {
    @Query("SELECT * FROM recent_apps ORDER BY lastLaunchedAt DESC LIMIT 12")
    fun observeRecent(): Flow<List<RecentAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordLaunch(entry: RecentAppEntity)
}

@Database(
    entities = [
        DesktopItemEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        CalendarEventEntity::class,
        RecentAppEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DenDatabase : RoomDatabase() {
    abstract fun desktopItemDao(): DesktopItemDao
    abstract fun browserDao(): BrowserDao
    abstract fun calendarDao(): CalendarDao
    abstract fun recentAppDao(): RecentAppDao

    companion object {
        @Volatile
        private var INSTANCE: DenDatabase? = null

        fun getInstance(context: Context): DenDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DenDatabase::class.java,
                    "denwindows_v1.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
