package com.example.game.data

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
import com.example.game.model.PlayerSaveData
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "save_slots")
data class SaveSlotEntity(
    @PrimaryKey val slotId: String,
    val characterName: String,
    val vocationName: String,
    val level: Int,
    val floor: Int,
    val updatedAtMs: Long,
    val payloadJson: String
)

@Dao
interface SaveSlotDao {
    @Query("SELECT * FROM save_slots ORDER BY updatedAtMs DESC")
    fun observeAllSaves(): Flow<List<SaveSlotEntity>>

    @Query("SELECT * FROM save_slots WHERE slotId = :slotId LIMIT 1")
    suspend fun getSaveById(slotId: String): SaveSlotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSave(entity: SaveSlotEntity)

    @Query("DELETE FROM save_slots WHERE slotId = :slotId")
    suspend fun deleteSave(slotId: String)
}

@Database(entities = [SaveSlotEntity::class], version = 1, exportSchema = false)
abstract class RookvaleDatabase : RoomDatabase() {
    abstract fun saveSlotDao(): SaveSlotDao

    companion object {
        @Volatile
        private var INSTANCE: RookvaleDatabase? = null

        fun getInstance(context: Context): RookvaleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RookvaleDatabase::class.java,
                    "rookvale_offline_saves.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class GameSaveRepository(private val dao: SaveSlotDao) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(PlayerSaveData::class.java)

    val allSaves: Flow<List<SaveSlotEntity>> = dao.observeAllSaves()

    suspend fun saveGame(data: PlayerSaveData) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val safeData = data.copy(
            backpack = data.backpack.filter { it.itemId.isNotBlank() && it.count > 0 }.toList(),
            toolBag = data.toolBag.filter { it.itemId.isNotBlank() && it.count > 0 }.toList(),
            depot = data.depot.filter { it.itemId.isNotBlank() && it.count > 0 }.toList()
        )
        val json = adapter.toJson(safeData)
        dao.upsertSave(
            SaveSlotEntity(
                slotId = safeData.slotId,
                characterName = safeData.name,
                vocationName = safeData.vocationName,
                level = safeData.level,
                floor = safeData.floor,
                updatedAtMs = System.currentTimeMillis(),
                payloadJson = json
            )
        )
    }

    suspend fun loadGame(slotId: String): PlayerSaveData? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val entity = dao.getSaveById(slotId) ?: return@withContext null
        runCatching { adapter.fromJson(entity.payloadJson) }.getOrNull()?.let { parsed ->
            parsed.copy(
                backpack = parsed.backpack.orEmpty().filter { it.itemId.isNotBlank() && it.count > 0 }.toList(),
                toolBag = parsed.toolBag.orEmpty().filter { it.itemId.isNotBlank() && it.count > 0 }.toList(),
                depot = parsed.depot.orEmpty().filter { it.itemId.isNotBlank() && it.count > 0 }.toList()
            )
        }
    }

    suspend fun deleteSave(slotId: String) {
        dao.deleteSave(slotId)
    }
}
