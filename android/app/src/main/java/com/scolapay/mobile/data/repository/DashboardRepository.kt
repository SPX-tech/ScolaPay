package com.scolapay.mobile.data.repository

import com.scolapay.mobile.data.api.ApiClient
import com.scolapay.mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DashboardRepository {

    private val api = ApiClient.getApiService()

    suspend fun getSummary(): Result<DashboardSummary> = withContext(Dispatchers.IO) {
        try {
            val response = api.getDashboardSummary()
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Erreur lors du chargement du tableau de bord"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }

    suspend fun getMonthlyReport(year: Int, month: Int): Result<MonthlyReport> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getMonthlyReport(year, month)
                if (response.isSuccessful) Result.success(response.body()!!)
                else Result.failure(Exception("Erreur lors du chargement du rapport mensuel"))
            } catch (e: Exception) {
                Result.failure(Exception("Erreur réseau: ${e.message}"))
            }
        }

    suspend fun getClassReport(classId: Long): Result<ClassReport> = withContext(Dispatchers.IO) {
        try {
            val response = api.getClassReport(classId)
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Erreur lors du chargement du rapport de classe"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }

    suspend fun getStudentDebts(): Result<List<StudentDebt>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getStudentDebts()
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Erreur lors du chargement des dettes"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }
}
