package com.alinam.smartconnect.mobile.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.alinam.smartconnect.mobile.data.db.entity.DeviceSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: DeviceSettingsEntity)

    @Query("SELECT value FROM device_settings WHERE \`key\` = :key")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM device_settings")
    fun getAllSettings(): Flow<List<DeviceSettingsEntity>>

    @Query("DELETE FROM device_settings WHERE \`key\` = :key")
    suspend fun delete(key: String)
}
