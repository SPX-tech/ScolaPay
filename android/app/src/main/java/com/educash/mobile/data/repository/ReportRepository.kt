package com.educash.mobile.data.repository

import com.educash.mobile.data.api.ApiClient
import com.educash.mobile.data.model.RevenueReport
import com.educash.mobile.data.model.StudentDebt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReportRepository {

    private val api = ApiClient.getApiService()

    suspend fun getRevenueReport(startDate: String, endDate: String): Result<RevenueReport> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getRevenueReport(startDate, endDate)
                if (response.isSuccessful) Result.success(response.body()!!)
                else Result.failure(Exception("Erreur lors du chargement du rapport"))
            } catch (e: Exception) {
                Result.failure(Exception("Erreur réseau: ${e.message}"))
            }
        }

    suspend fun getDebtsReport(classId: Long? = null): Result<List<StudentDebt>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getDebtsReport(classId)
                if (response.isSuccessful) Result.success(response.body()!!)
                else Result.failure(Exception("Erreur lors du chargement des dettes"))
            } catch (e: Exception) {
                Result.failure(Exception("Erreur réseau: ${e.message}"))
            }
        }
}
