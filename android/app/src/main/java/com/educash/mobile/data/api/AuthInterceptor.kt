package com.educash.mobile.data.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import okhttp3.Interceptor
import okhttp3.Response
import com.educash.mobile.util.Constants

class AuthInterceptor(context: Context) : Interceptor {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs = EncryptedSharedPreferences.create(
        Constants.PREFS_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = prefs.getString(Constants.KEY_TOKEN, null)
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }

    fun saveToken(token: String) {
        prefs.edit().putString(Constants.KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(Constants.KEY_TOKEN, null)

    fun clearToken() {
        prefs.edit().remove(Constants.KEY_TOKEN).apply()
    }

    fun saveUserInfo(userId: Long, userName: String, userRole: String) {
        prefs.edit()
            .putLong(Constants.KEY_USER_ID, userId)
            .putString(Constants.KEY_USER_NAME, userName)
            .putString(Constants.KEY_USER_ROLE, userRole)
            .apply()
    }

    fun getUserId(): Long = prefs.getLong(Constants.KEY_USER_ID, 0)

    fun getUserName(): String? = prefs.getString(Constants.KEY_USER_NAME, null)

    fun getUserRole(): String? = prefs.getString(Constants.KEY_USER_ROLE, null)

    fun clearSession() {
        prefs.edit()
            .remove(Constants.KEY_TOKEN)
            .remove(Constants.KEY_USER_ID)
            .remove(Constants.KEY_USER_NAME)
            .remove(Constants.KEY_USER_ROLE)
            .apply()
    }
}
