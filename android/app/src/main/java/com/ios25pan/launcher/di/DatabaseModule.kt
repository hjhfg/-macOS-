package com.ios25pan.launcher.di

import android.content.Context
import com.ios25pan.launcher.data.db.DesktopDao
import com.ios25pan.launcher.data.db.LauncherDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LauncherDatabase =
        LauncherDatabase.build(context)

    @Provides
    fun provideDesktopDao(db: LauncherDatabase): DesktopDao = db.desktopDao()
}
