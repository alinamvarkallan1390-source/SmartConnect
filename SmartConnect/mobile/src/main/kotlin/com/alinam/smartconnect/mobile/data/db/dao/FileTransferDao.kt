package com.alinam.smartconnect.mobile.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.alinam.smartconnect.mobile.data.db.entity.FileTransferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileTransferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transfer: FileTransferEntity)

    @Update
    suspend fun update(transfer: FileTransferEntity)

    @Query("SELECT * FROM file_transfers ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<FileTransferEntity>>

    @Query("SELECT * FROM file_transfers WHERE transferId = :id")
    suspend fun getById(id: String): FileTransferEntity?

    @Query("UPDATE file_transfers SET status = :status, progress = :progress WHERE transferId = :id")
    suspend fun updateProgress(id: String, status: String, progress: Float)

    @Query("UPDATE file_transfers SET status = 'COMPLETED', completedAt = :time, progress = 1.0 WHERE transferId = :id")
    suspend fun markCompleted(id: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE file_transfers SET status = 'FAILED' WHERE transferId = :id")
    suspend fun markFailed(id: String)

    @Query("DELETE FROM file_transfers WHERE transferId = :id")
    suspend fun delete(id: String)
}
