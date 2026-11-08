package com.example.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CommandHistoryRepository(private val dao: CommandHistoryDao) {

    val allHistory: Flow<List<CommandHistoryEntity>> = dao.getAllHistory()

    suspend fun recordCommand(
        command: String,
        sessionId: String = "default",
        directory: String = "/home/user",
        exitCode: Int = 0
    ) = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return@withContext

        // Remove identical previous command to avoid adjacent clutter
        dao.deleteByCommand(trimmed)

        val entity = CommandHistoryEntity(
            command = trimmed,
            sessionId = sessionId,
            directory = directory,
            timestamp = System.currentTimeMillis(),
            exitCode = exitCode
        )
        dao.insertCommand(entity)
    }

    suspend fun getNavigationHistory(): List<String> = withContext(Dispatchers.IO) {
        dao.getCommandListAsc()
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearHistory()
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        dao.count()
    }
}
