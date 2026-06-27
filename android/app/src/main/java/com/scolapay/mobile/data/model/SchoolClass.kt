package com.scolapay.mobile.data.model

data class SchoolClass(
    val id: String,
    val name: String,
    val description: String?,
    val academicYear: String,
    val status: String,
    val studentCount: Int = 0,
    val createdAt: String
)

data class CreateClassRequest(
    val name: String,
    val description: String? = null,
    val academicYear: String? = null
)

data class UpdateClassRequest(
    val name: String? = null,
    val description: String? = null,
    val status: String? = null
)

data class Tenant(
    val id: String,
    val code: String,
    val name: String,
    val currency: String = "XAF",
    val timezone: String = "Africa/Ndjamena"
)

data class UpdateTenantRequest(
    val name: String? = null,
    val currency: String? = null,
    val timezone: String? = null
)
