package com.srk.wifianalyzer.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.srk.wifianalyzer.data.repository.WifiRepositoryImpl
import com.srk.wifianalyzer.domain.repository.WifiRepository
import com.srk.wifianalyzer.settings.data.SettingsRepositoryImpl
import com.srk.wifianalyzer.settings.domain.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindWifiRepository(impl: WifiRepositoryImpl): WifiRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    companion object {
        @Provides
        @Singleton
        fun provideWifiManager(@ApplicationContext context: Context): WifiManager {
            @Suppress("DEPRECATION")
            return context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        }

        @Provides
        @Singleton
        fun provideConnectivityManager(@ApplicationContext context: Context): ConnectivityManager {
            return context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        }

        @Provides
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        fun provideAppScope(ioDispatcher: CoroutineDispatcher): CoroutineScope {
            return CoroutineScope(SupervisorJob() + ioDispatcher)
        }
    }
}
