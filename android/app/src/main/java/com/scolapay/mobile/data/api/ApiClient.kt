package com.scolapay.mobile.data.api

import android.content.Context
import com.scolapay.mobile.util.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var apiService: ApiService? = null
    private var authInterceptor: AuthInterceptor? = null

    fun initialize(context: Context, baseUrl: String = Constants.DEFAULT_API_URL) {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }

        authInterceptor = AuthInterceptor(context)

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor!!)
            .addInterceptor(logging)
            .addInterceptor(ResponseLimitInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }

    fun getApiService(): ApiService {
        return apiService ?: throw IllegalStateException("ApiClient not initialized. Call initialize() first.")
    }

    fun getAuthInterceptor(): AuthInterceptor {
        return authInterceptor ?: throw IllegalStateException("ApiClient not initialized. Call initialize() first.")
    }
}
