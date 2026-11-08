package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val command: String,
    val sessionId: String = "default",
    val directory: String = "/home/user",
    val timestamp: Long = System.currentTimeMillis(),
    val exitCode: Int = 0
)
