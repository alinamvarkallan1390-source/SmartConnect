package com.alinam.smartconnect.mobile.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.alinam.smartconnect.mobile.data.db.dao.ConnectionLogDao
import com.alinam.smartconnect.mobile.data.db.dao.DeviceSettingsDao
import com.alinam.smartconnect.mobile.data.db.dao.FileTransferDao
import com.alinam.smartconnect.mobile.data.db.entity.ConnectionLogEntity
import com.alinam.smartconnect.mobile.data.db.entity.DeviceSettingsEntity
import com.alinam.smartconnect.mobile.data.db.entity.FileTransferEntity

@Database(
    entities = [
        ConnectionLogEntity::class,
        FileTransferEntity::class,
        DeviceSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SmartConnectDatabase : RoomDatabase() {
    abstract fun connectionLogDao(): ConnectionLogDao
    abstract fun fileTransferDao(): FileTransferDao
    abstract fun deviceSettingsDao(): DeviceSettingsDao

    companion object {
        private const val DB_NAME = "smartconnect.db"

        @Volatile private var INSTANCE: SmartConnectDatabase? = null

        fun getInstance(context: Context): SmartConnectDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, SmartConnectDatabase::class.java, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
