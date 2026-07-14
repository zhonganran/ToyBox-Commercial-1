package com.byd.wjx.di

import com.byd.wjx.data.nativestack.NativeParsReader
import com.byd.wjx.data.nativestack.ParsReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NativeModule {

    @Binds
    @Singleton
    abstract fun bindParsReader(impl: NativeParsReader): ParsReader
}
