package com.scolapay.mobile.data.repository

import com.scolapay.mobile.data.api.ApiClient
import com.scolapay.mobile.data.model.RevenueReport
import com.scolapay.mobile.data.model.StudentDebt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReportRepository {

    private val api = ApiClient.getApiService()

    suspend fun getRevenueReport(startDate: String, endDate: String): Result<RevenueReport> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getRevenueReport(startDate, endDate)
                if (response.isSuccessful) {
                    val summary = response.body()!!.summary!!
                    Result.success(RevenueReport(summary.totalCollectedCents.toBigDecimal().movePointLeft(2), summary.transactionCount))
                }
                else Result.failure(Exception("Erreur lors du chargement du rapport"))
            } catch (e: Exception) {
                Result.failure(Exception("Erreur réseau: ${e.message}"))
            }
        }

    suspend fun getDebtsReport(): Result<List<StudentDebt>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getDebtsReport()
                if (response.isSuccessful) Result.success(response.body()!!.filter { it.debtCents > 0 }.map { StudentDebt(it.id, it.fullName, it.className, it.debtCents.toBigDecimal().movePointLeft(2)) })
                else Result.failure(Exception("Erreur lors du chargement des dettes"))
            } catch (e: Exception) {
                Result.failure(Exception("Erreur réseau: ${e.message}"))
            }
        }
}
