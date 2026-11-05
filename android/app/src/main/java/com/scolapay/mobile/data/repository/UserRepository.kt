package com.scolapay.mobile.data.repository

import com.scolapay.mobile.data.api.ApiClient
import com.scolapay.mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository {

    private val api = ApiClient.getApiService()

    suspend fun getUsers(): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getUsers()
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Erreur lors du chargement des utilisateurs"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }

    suspend fun createUser(request: CreateUserRequest): Result<User> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.createUser(request)
                if (response.isSuccessful) Result.success(response.body()!!)
                else Result.failure(Exception("Erreur lors de la création"))
            } catch (e: Exception) {
                Result.failure(Exception("Erreur réseau: ${e.message}"))
            }
        }

    suspend fun updateUser(id: String, request: UpdateUserRequest): Result<User> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.updateUser(id, request)
                if (response.isSuccessful) Result.success(response.body()!!)
                else Result.failure(Exception("Erreur lors de la modification"))
            } catch (e: Exception) {
                Result.failure(Exception("Erreur réseau: ${e.message}"))
            }
        }

    suspend fun deleteUser(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteUser(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Erreur lors de la suppression"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }
}
