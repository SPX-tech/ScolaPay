package com.scolapay.mobile.data.model

import com.google.gson.annotations.SerializedName

data class Student(
    val id: String,
    val tenantId: String,
    val classId: String?,
    val studentNumber: String,
    val fullName: String,
    val guardianPhone: String?,
    val guardianName: String?,
    val status: String,
    val debtCents: Long,
    val creditCents: Long,
    val enrolledAt: String?,
    val lastPaymentAt: String?,
    @SerializedName("class") val classInfo: ClassInfo?,
    val className: String?
) {
    val debtFormatted: String get() = "${debtCents / 100} XAF"
    val creditFormatted: String get() = "${creditCents / 100} XAF"
    val hasDebt: Boolean get() = debtCents > 0
    val hasCredit: Boolean get() = creditCents > 0
    val isActive: Boolean get() = status == "ACTIVE"
    val displayClassName: String? get() = classInfo?.name ?: className
}

data class ClassInfo(
    val id: String,
    val name: String
)

data class StudentListResponse(
    val data: List<Student>,
    val total: Int,
    val page: Int,
    val limit: Int
) {
    val hasMore: Boolean get() = page * limit < total
}

data class CreateStudentRequest(
    val classId: String,
    val fullName: String,
    val guardianPhone: String? = null,
    val guardianName: String? = null,
    val enrolledAt: String? = null
)

data class UpdateStudentRequest(
    val classId: String? = null,
    val fullName: String? = null,
    val guardianPhone: String? = null,
    val guardianName: String? = null,
    val status: String? = null
)

data class ImportStudentsRequest(
    val students: List<ImportStudentItem>
)

data class ImportStudentItem(
    val fullName: String,
    val classId: String? = null,
    val guardianPhone: String? = null,
    val guardianName: String? = null
)
