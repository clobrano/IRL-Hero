package com.clobrano.irlhero.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "raw_event", indices = [Index(value = ["type", "timestampUtc"], unique = true)])
data class RawEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val timestampUtc: Long,
)

@Dao
interface RawEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<RawEventEntity>)

    @Query("SELECT * FROM raw_event ORDER BY timestampUtc")
    suspend fun all(): List<RawEventEntity>

    @Query("DELETE FROM raw_event WHERE timestampUtc < :before")
    suspend fun deleteBefore(before: Long)

    @Query("DELETE FROM raw_event")
    suspend fun deleteAll()
}

@Database(entities = [RawEventEntity::class], version = 1, exportSchema = true)
abstract class IrlDatabase : RoomDatabase() {
    abstract fun rawEvents(): RawEventDao

    companion object {
        @Volatile private var instance: IrlDatabase? = null

        fun get(context: Context): IrlDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, IrlDatabase::class.java, "irl.db")
                .build().also { instance = it }
        }
    }
}
