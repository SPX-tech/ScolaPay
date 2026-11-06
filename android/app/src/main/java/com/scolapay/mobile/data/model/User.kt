package com.scolapay.mobile.data.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: String,
    val tenantId: String,
    val email: String,
    val phone: String?,
    val fullName: String,
    val role: String,
    val status: String
) {
    val firstName: String get() = fullName.substringBefore(' ')
    val lastName: String get() = fullName.substringAfter(' ', "")
    val isActive: Boolean get() = status == "ACTIVE"
    val roleLabel: String
        get() = when (role) {
            "TENANT_ADMIN" -> "Administrateur"
            "CASHIER" -> "Caissier"
            "ACCOUNTANT" -> "Comptable"
            "AUDITOR" -> "Auditeur"
            else -> role
        }
}

data class LoginResponse(
    val accessToken: String,
    val expiresInSeconds: Int,
    val user: User
)

data class LoginRequest(
    val tenantCode: String,
    val email: String,
    val password: String
)
