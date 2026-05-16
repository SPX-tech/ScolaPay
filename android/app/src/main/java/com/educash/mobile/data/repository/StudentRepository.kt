package com.educash.mobile.data.repository

import com.educash.mobile.data.api.ApiClient
import com.educash.mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StudentRepository {

    private val api = ApiClient.getApiService()

    suspend fun getStudents(
        page: Int = 0,
        limit: Int = 50,
        search: String? = null,
        classId: Long? = null,
        status: String? = null
    ): Result<StudentListResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getStudents(page, limit, search, classId, status)
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Erreur lors du chargement des élèves"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }

    suspend fun getStudent(id: Long): Result<Student> = withContext(Dispatchers.IO) {
        try {
            val response = api.getStudent(id)
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Élève introuvable"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }

    suspend fun createStudent(request: CreateStudentRequest): Result<Student> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.createStudent(request)
                if (response.isSuccessful) Result.success(response.body()!!)
                else Result.failure(Exception("Erreur lors de la création"))
            } catch (e: Exception) {
                Result.failure(Exception("Erreur réseau: ${e.message}"))
            }
        }

    suspend fun updateStudent(id: Long, request: UpdateStudentRequest): Result<Student> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.updateStudent(id, request)
                if (response.isSuccessful) Result.success(response.body()!!)
                else Result.failure(Exception("Erreur lors de la modification"))
            } catch (e: Exception) {
                Result.failure(Exception("Erreur réseau: ${e.message}"))
            }
        }

    suspend fun deleteStudent(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteStudent(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Erreur lors de la suppression"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }
}
