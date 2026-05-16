package com.educash.mobile.data.model

data class DashboardSummary(
    val totalStudents: Int = 0,
    val activeStudents: Int = 0,
    val totalClasses: Int = 0,
    val totalCollectedCents: String = "0",
    val currentMonthCents: String = "0",
    val todayTransactions: Int = 0,
    val pendingVoidRequests: Int = 0,
    val recentTransactions: List<RecentTransaction> = emptyList()
)

data class RecentTransaction(
    val id: String,
    val studentName: String,
    val amountCents: Long,
    val status: String,
    val recordedAt: String
)

data class Receipt(
    val receiptNumber: String,
    val receiptHash: String,
    val qrCode: String,
    val transaction: Map<String, Any>
)

data class RecordCashPaymentRequest(
    val studentId: String,
    val amountCents: Int,
    val idempotencyKey: String? = null,
    val note: String? = null
)

// Report models
data class ReportCollection(
    val dailyCollections: List<DailyCollection> = emptyList(),
    val topPayers: List<TopPayer> = emptyList(),
    val summary: ReportSummary? = null
)

data class DailyCollection(
    val date: String,
    val totalCents: String,
    val transactionCount: Int = 0
)

data class TopPayer(
    val studentId: String,
    val fullName: String,
    val studentNumber: String,
    val totalCents: String,
    val transactionCount: Int = 0
)

data class ReportSummary(
    val totalCollectedCents: String = "0",
    val transactionCount: Int = 0
)

data class DebtStudent(
    val id: String,
    val studentNumber: String,
    val fullName: String,
    val className: String?,
    val debtCents: Long,
    val creditCents: Long,
    val guardianPhone: String?,
    val status: String = "ACTIVE"
)

data class CollectionsReportQuery(
    val startDate: String? = null,
    val endDate: String? = null
)
