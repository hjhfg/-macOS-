package com.ios25pan.launcher.di

import com.ios25pan.launcher.data.window.FreeformController
import com.ios25pan.launcher.data.window.ShizukuFreeformController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WindowModule {

    @Binds
    @Singleton
    abstract fun bindFreeformController(impl: ShizukuFreeformController): FreeformController
}
