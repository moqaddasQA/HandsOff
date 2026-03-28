package com.moqaddas.handsoff.di

import android.content.Context
import androidx.room.Room
import com.moqaddas.handsoff.data.db.AppDatabase
import com.moqaddas.handsoff.data.db.PermissionSnapshotDao
import com.moqaddas.handsoff.data.db.ThreatEventDao
import com.moqaddas.handsoff.data.shizuku.ShizukuCommandRunner
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
    fun provideShizukuCommandRunner(): ShizukuCommandRunner = ShizukuCommandRunner()

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "handsoff.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideThreatEventDao(db: AppDatabase): ThreatEventDao = db.threatEventDao()

    @Provides
    @Singleton
    fun providePermissionSnapshotDao(db: AppDatabase): PermissionSnapshotDao = db.permissionSnapshotDao()
}
