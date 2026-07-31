package com.alinam.smartconnect.mobile.di

import android.content.Context
import androidx.room.Room
import com.alinam.smartconnect.mobile.data.db.SmartConnectDatabase
import com.alinam.smartconnect.mobile.data.db.dao.ConnectionLogDao
import com.alinam.smartconnect.mobile.data.db.dao.DeviceSettingsDao
import com.alinam.smartconnect.mobile.data.db.dao.FileTransferDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): SmartConnectDatabase =
        Room.databaseBuilder(ctx, SmartConnectDatabase::class.java, "smartconnect.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideConnectionLogDao(db: SmartConnectDatabase): ConnectionLogDao = db.connectionLogDao()

    @Provides
    fun provideFileTransferDao(db: SmartConnectDatabase): FileTransferDao = db.fileTransferDao()

    @Provides
    fun provideDeviceSettingsDao(db: SmartConnectDatabase): DeviceSettingsDao = db.deviceSettingsDao()
}
