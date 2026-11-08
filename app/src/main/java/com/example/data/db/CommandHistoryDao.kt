package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandHistoryDao {

    @Query("SELECT * FROM command_history ORDER BY id ASC")
    fun getAllHistory(): Flow<List<CommandHistoryEntity>>

    @Query("SELECT command FROM command_history ORDER BY id ASC")
    suspend fun getCommandListAsc(): List<String>

    @Query("SELECT * FROM command_history ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentCommands(limit: Int = 200): List<CommandHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(entry: CommandHistoryEntity): Long

    @Query("DELETE FROM command_history WHERE command = :cmd")
    suspend fun deleteByCommand(cmd: String)

    @Query("DELETE FROM command_history")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM command_history")
    suspend fun count(): Int
}
