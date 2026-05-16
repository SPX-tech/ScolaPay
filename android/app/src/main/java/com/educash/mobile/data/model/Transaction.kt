package com.educash.mobile.data.model

data class Transaction(
    val id: String,
    val studentId: String,
    val student: TransactionStudent?,
    val recordedBy: TransactionUser?,
    val type: String,
    val status: String,
    val amountCents: Long,
    val previousDebtCents: Long,
    val newDebtCents: Long,
    val currency: String = "XAF",
    val receiptNumber: String?,
    val note: String?,
    val recordedAt: String,
    val voidedAt: String?,
    val voidReason: String?
) {
    val studentName: String get() = student?.fullName ?: ""
    val studentNumber: String get() = student?.studentNumber ?: ""
    val recordedByName: String get() = recordedBy?.fullName ?: ""
    val amountFormatted: String get() = "${amountCents / 100} $currency"
    val isPosted: Boolean get() = status == "POSTED"
    val isVoided: Boolean get() = status == "VOIDED"
    val isCashPayment: Boolean get() = type == "CASH_PAYMENT"

    val typeLabel: String
        get() = when (type) {
            "CASH_PAYMENT" -> "Encaissement"
            "DEBT_ADJUSTMENT" -> "Ajustement"
            "REFUND" -> "Remboursement"
            else -> type
        }

    val statusLabel: String
        get() = when (status) {
            "POSTED" -> "Validé"
            "VOIDED" -> "Annulé"
            else -> status
        }
}

data class TransactionStudent(
    val id: String,
    val fullName: String,
    val studentNumber: String
)

data class TransactionUser(
    val id: String,
    val fullName: String
)

data class TransactionListResponse(
    val data: List<Transaction>,
    val total: Int,
    val page: Int,
    val limit: Int
) {
    val hasMore: Boolean get() = page * limit < total
}

data class VoidTransactionRequest(
    val reason: String
)
