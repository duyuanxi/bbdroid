package com.bbdroid.app

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

// ---------- 历史记录 ----------
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val cover: String,
    val quality: String,
    val size: Long,          // 文件大小（字节）
    val path: String,        // 保存位置显示文本
    val time: Long,          // 时间戳（毫秒）
    val uri: String,         // 文件 Uri（可为空）
    val bvid: String,
)

// ---------- 下载队列 ----------
@Entity(tableName = "download_queue")
data class QueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val aid: String,
    val cid: String,
    val bvid: String,
    val quality: String,     // 清晰度描述（dfn）
    val status: String,      // PENDING / DONE
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY time DESC LIMIT 100")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY time DESC LIMIT 100")
    suspend fun loadAll(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history WHERE title = :title AND time = :time")
    suspend fun deleteByTitleTime(title: String, time: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM download_queue ORDER BY id ASC")
    fun observeAll(): Flow<List<QueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueEntity>)

    @Query("DELETE FROM download_queue")
    suspend fun clear()

    @Query("UPDATE download_queue SET status = 'DONE' WHERE id = :id")
    suspend fun markDone(id: Long)
}

@Database(entities = [HistoryEntity::class, QueueEntity::class], version = 1, exportSchema = false)
abstract class BbdroidDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao

    companion object {
        @Volatile
        private var INSTANCE: BbdroidDatabase? = null

        fun get(context: Context): BbdroidDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BbdroidDatabase::class.java,
                    "bbdroid.db",
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
