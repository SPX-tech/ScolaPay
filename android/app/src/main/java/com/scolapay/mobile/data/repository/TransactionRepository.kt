package com.scolapay.mobile.data.repository

import com.scolapay.mobile.data.api.ApiClient
import com.scolapay.mobile.data.model.Transaction
import com.scolapay.mobile.data.model.TransactionListResponse
import com.scolapay.mobile.data.model.VoidTransactionRequest
import com.scolapay.mobile.data.model.VoidTransactionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TransactionRepository {

    private val api = ApiClient.getApiService()

    suspend fun getTransactions(
        page: Int = 1,
        limit: Int = 50,
        studentId: String? = null,
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

    suspend fun voidTransaction(id: String, reason: String): Result<VoidTransactionResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.voidTransaction(id, VoidTransactionRequest(reason))
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Erreur lors de l'annulation"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }
}
