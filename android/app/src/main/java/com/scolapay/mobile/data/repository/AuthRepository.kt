package com.scolapay.mobile.data.repository

import com.scolapay.mobile.data.api.ApiClient
import com.scolapay.mobile.data.model.LoginRequest
import com.scolapay.mobile.data.model.LoginResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository {

    private val api = ApiClient.getApiService()
    private val authInterceptor = ApiClient.getAuthInterceptor()

    suspend fun login(tenantCode: String, email: String, password: String): Result<LoginResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.login(LoginRequest(tenantCode, email, password))
                if (response.isSuccessful) {
                    val loginResponse = response.body()!!
                    authInterceptor.saveToken(loginResponse.accessToken)
                    authInterceptor.saveUserInfo(
                        loginResponse.user.id,
                        loginResponse.user.fullName,
                        loginResponse.user.role
                    )
                    Result.success(loginResponse)
                } else {
                    Result.failure(Exception("Email ou mot de passe incorrect"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Erreur de connexion au serveur"))
            }
        }

    fun isLoggedIn(): Boolean {
        return authInterceptor.getToken() != null
    }

    fun getCurrentUserName(): String? {
        return authInterceptor.getUserName()
    }

    fun getCurrentUserRole(): String? {
        return authInterceptor.getUserRole()
    }

    fun logout() {
        authInterceptor.clearSession()
    }
}
