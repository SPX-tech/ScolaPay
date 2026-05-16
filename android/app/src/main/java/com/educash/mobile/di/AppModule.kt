package com.educash.mobile.di

import android.content.Context
import com.educash.mobile.EduCashApp
import com.educash.mobile.data.api.ApiClient
import com.educash.mobile.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideApiClient(@ApplicationContext context: Context): ApiClient {
        ApiClient.initialize(context)
        return ApiClient
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

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
