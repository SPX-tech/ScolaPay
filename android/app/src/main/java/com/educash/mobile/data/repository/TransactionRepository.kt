package com.educash.mobile.data.repository

import com.educash.mobile.data.api.ApiClient
import com.educash.mobile.data.model.Transaction
import com.educash.mobile.data.model.TransactionListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TransactionRepository {

    private val api = ApiClient.getApiService()

    suspend fun getTransactions(
        page: Int = 0,
        limit: Int = 50,
        studentId: Long? = null,
        status: String? = null,
        startDate: String? = null,
        endDate: String? = null
    ): Result<TransactionListResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTransactions(page, limit, studentId, status, startDate, endDate)
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Erreur lors du chargement des transactions"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }

    suspend fun voidTransaction(id: Long): Result<Transaction> = withContext(Dispatchers.IO) {
        try {
            val response = api.voidTransaction(id)
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Erreur lors de l'annulation"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }
}
