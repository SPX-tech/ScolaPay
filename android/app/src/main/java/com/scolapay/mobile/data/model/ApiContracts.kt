package com.scolapay.mobile.data.model

import java.math.BigDecimal

data class PaginationMeta(val total: Int, val page: Int, val limit: Int, val totalPages: Int)
data class CreateUserRequest(val email: String, val password: String, val fullName: String, val role: String = "CASHIER")
data class UpdateUserRequest(val email: String? = null, val password: String? = null, val fullName: String? = null, val role: String? = null)
data class RecordCashPaymentResponse(val transactionId: String, val amountCents: String, val newDebtCents: String, val recordedAt: String, val receiptNumber: String)
data class VoidTransactionResponse(val message: String, val transactionId: String)

// Modèles de présentation construits à partir du rapport de collecte existant.
data class RevenueReport(val totalRevenue: BigDecimal, val totalTransactions: Int, val byPaymentMethod: Map<String, BigDecimal> = emptyMap())
data class MonthlyReport(val totalRevenue: BigDecimal, val totalTransactions: Int, val averageTransaction: BigDecimal, val totalStudents: Int = 0)
data class StudentDebt(val studentId: String, val fullName: String, val className: String?, val amount: BigDecimal)
