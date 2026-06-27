package com.scolapay.mobile.di

import android.content.Context
import com.scolapay.mobile.data.api.ApiClient
import com.scolapay.mobile.data.repository.*
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
    fun provideApiClient(@ApplicationContext context: Context): ApiClient {
        ApiClient.initialize(context)
        return ApiClient
    }

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository = AuthRepository()

    @Provides
    @Singleton
    fun provideStudentRepository(): StudentRepository = StudentRepository()

    @Provides
    @Singleton
    fun provideDashboardRepository(): DashboardRepository = DashboardRepository()

    @Provides
    @Singleton
    fun provideLedgerRepository(): LedgerRepository = LedgerRepository()

    @Provides
    @Singleton
    fun provideTransactionRepository(): TransactionRepository = TransactionRepository()

    @Provides
    @Singleton
    fun provideUserRepository(): UserRepository = UserRepository()

    @Provides
    @Singleton
    fun provideReportRepository(): ReportRepository = ReportRepository()

    @Provides
    @Singleton
    fun provideClassRepository(): ClassRepository = ClassRepository()
}
