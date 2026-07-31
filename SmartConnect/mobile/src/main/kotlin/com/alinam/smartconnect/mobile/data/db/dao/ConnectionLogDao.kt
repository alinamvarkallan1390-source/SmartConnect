package com.alinam.smartconnect.mobile.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.alinam.smartconnect.mobile.data.db.entity.ConnectionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ConnectionLogEntity)

    @Query("SELECT * FROM connection_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllLogs(): Flow<List<ConnectionLogEntity>>

    @Query("SELECT * FROM connection_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastConnection(): ConnectionLogEntity?

    @Query("SELECT deviceAddress FROM connection_logs WHERE eventType = 'CONNECTED' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastConnectedAddress(): String?

    @Query("DELETE FROM connection_logs WHERE timestamp < :cutoff")
    suspend fun deleteOldLogs(cutoff: Long)

    @Query("SELECT COUNT(*) FROM connection_logs WHERE eventType = 'CONNECTED' AND timestamp > :since")
    suspend fun countConnectionsSince(since: Long): Int
}
