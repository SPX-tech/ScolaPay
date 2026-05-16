package com.educash.mobile.data.repository

import com.educash.mobile.data.api.ApiClient
import com.educash.mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ClassRepository {

    private val api = ApiClient.getApiService()

    suspend fun getClasses(): Result<List<SchoolClass>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getClasses()
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Erreur lors du chargement des classes"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }

    suspend fun createClass(request: CreateClassRequest): Result<SchoolClass> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.createClass(request)
                if (response.isSuccessful) Result.success(response.body()!!)
                else Result.failure(Exception("Erreur lors de la création"))
            } catch (e: Exception) {
                Result.failure(Exception("Erreur réseau: ${e.message}"))
            }
        }

    suspend fun updateClass(id: Long, request: UpdateClassRequest): Result<SchoolClass> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.updateClass(id, request)
                if (response.isSuccessful) Result.success(response.body()!!)
                else Result.failure(Exception("Erreur lors de la modification"))
            } catch (e: Exception) {
                Result.failure(Exception("Erreur réseau: ${e.message}"))
            }
        }

    suspend fun deleteClass(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteClass(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Erreur lors de la suppression"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }
}
