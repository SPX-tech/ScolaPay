package com.scolapay.mobile.data.repository

import com.scolapay.mobile.data.api.ApiClient
import com.scolapay.mobile.data.model.RecordCashPaymentRequest
import com.scolapay.mobile.data.model.RecordCashPaymentResponse
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LedgerRepository {

    private val api = ApiClient.getApiService()

    suspend fun recordCashPayment(
        studentId: String,
        amountCents: String,
        idempotencyKey: String,
        description: String? = null
    ): Result<RecordCashPaymentResponse> = withContext(Dispatchers.IO) {
        try {
            val request = RecordCashPaymentRequest(studentId, amountCents, idempotencyKey, description)
            val response = api.recordCashPayment(request)
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Erreur lors de l'enregistrement du paiement"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau: ${e.message}"))
        }
    }
}
