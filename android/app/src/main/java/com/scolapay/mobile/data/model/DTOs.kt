package com.scolapay.mobile.data.model

import com.google.gson.annotations.SerializedName

// ===== Auth =====
data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    val user: User
)

// ===== Students =====
data class CreateStudentRequest(
    val firstName: String,
    val lastName: String,
    val email: String? = null,
    val phone: String? = null,
    val classId: Long,
    val matricule: String? = null
)

data class UpdateStudentRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val classId: Long? = null,
    val matricule: String? = null,
    val status: String? = null
)

data class StudentListResponse(
    val data: List<Student>,
    val meta: PaginationMeta
)

data class PaginationMeta(
    val total: Int,
    val page: Int,
    val limit: Int,
    @SerializedName("totalPages") val totalPages: Int
)

// ===== Classes =====
data class CreateClassRequest(
    val name: String,
    val level: String? = null,
    val description: String? = null
)

data class UpdateClassRequest(
    val name: String? = null,
    val level: String? = null,
    val description: String? = null
)

// ===== Ledger =====
data class RecordCashPaymentRequest(
    @SerializedName("studentId") val studentId: Long,
    val amount: Double,
    @SerializedName("paymentMethod") val paymentMethod: String = "CASH",
    val description: String? = null
)

// ===== Transactions =====
data class TransactionListResponse(
    val data: List<Transaction>,
    val meta: PaginationMeta
)

// ===== Users =====
data class CreateUserRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val role: String = "CASHIER"
)

data class UpdateUserRequest(
    val email: String? = null,
    val password: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String? = null,
    val isActive: Boolean? = null
)

// ===== Reports =====
data class RevenueReport(
    @SerializedName("totalRevenue") val totalRevenue: Double,
    @SerializedName("totalTransactions") val totalTransactions: Int,
    @SerializedName("byPaymentMethod") val byPaymentMethod: Map<String, Double> = emptyMap(),
    @SerializedName("dailyBreakdown") val dailyBreakdown: List<DailyRevenue> = emptyList()
)

data class DailyRevenue(
    val date: String,
    val amount: Double,
    val count: Int
)

// ===== Dashboard =====
data class DashboardSummary(
    @SerializedName("totalRevenue") val totalRevenue: Double = 0.0,
    @SerializedName("totalTransactions") val totalTransactions: Int = 0,
    @SerializedName("activeStudents") val activeStudents: Int = 0,
    @SerializedName("totalDebts") val totalDebts: Double = 0.0
)

data class MonthlyReport(
    @SerializedName("totalRevenue") val totalRevenue: Double = 0.0,
    @SerializedName("totalTransactions") val totalTransactions: Int = 0,
    @SerializedName("averageTransaction") val averageTransaction: Double = 0.0,
    @SerializedName("totalStudents") val totalStudents: Int = 0
)

data class ClassReport(
    @SerializedName("classId") val classId: Long,
    @SerializedName("className") val className: String,
    @SerializedName("totalStudents") val totalStudents: Int,
    @SerializedName("totalPaid") val totalPaid: Double,
    @SerializedName("totalDue") val totalDue: Double
)

data class StudentDebt(
    @SerializedName("studentId") val studentId: Long,
    @SerializedName("studentFirstName") val studentFirstName: String,
    @SerializedName("studentLastName") val studentLastName: String,
    @SerializedName("className") val className: String? = null,
    val amount: Double
)
