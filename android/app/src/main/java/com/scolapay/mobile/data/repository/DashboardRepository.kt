package com.scolapay.mobile.data.repository

import com.scolapay.mobile.data.api.ApiClient
import com.scolapay.mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.math.RoundingMode

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
                val period = YearMonth.of(year, month)
                val response = api.getRevenueReport(period.atDay(1).toString(), period.atEndOfMonth().toString())
                if (response.isSuccessful) {
                    val report = response.body()!!.summary!!
                    val total = report.totalCollectedCents.toBigDecimal().movePointLeft(2)
                    val average = if (report.transactionCount == 0) java.math.BigDecimal.ZERO else total.divide(report.transactionCount.toBigDecimal(), 2, RoundingMode.HALF_UP)
                    Result.success(MonthlyReport(total, report.transactionCount, average))
                }
                else Result.failure(Exception("Erreur lors du chargement du rapport mensuel"))
            } catch (e: Exception) {
                Result.failure(Exception("Erreur réseau: ${e.message}"))
            }
        }

}
