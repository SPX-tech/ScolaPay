package com.educash.mobile.data.repository

import com.educash.mobile.data.api.ApiClient
import com.educash.mobile.data.model.RecordCashPaymentRequest
import com.educash.mobile.data.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LedgerRepository {

    private val api = ApiClient.getApiService()

    suspend fun recordCashPayment(
        studentId: Long,
        amount: Double,
        paymentMethod: String = "CASH",
        description: String? = null
    ): Result<Transaction> = withContext(Dispatchers.IO) {
        try {
            val request = RecordCashPaymentRequest(studentId, amount, paymentMethod, description)
            val response = api.recordCashPayment(request)
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Erreur lors de l'enregistrement du paiement"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }
}
