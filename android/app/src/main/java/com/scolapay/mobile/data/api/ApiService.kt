package com.scolapay.mobile.data.api

import com.scolapay.mobile.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ===== Auth =====
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // ===== Dashboard =====
    @GET("dashboard/summary")
    suspend fun getDashboardSummary(): Response<DashboardSummary>

    // ===== Students =====
    @GET("students")
    suspend fun getStudents(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("search") search: String? = null,
        @Query("classId") classId: String? = null,
        @Query("status") status: String? = null
    ): Response<StudentListResponse>

    @GET("students/{id}")
    suspend fun getStudent(@Path("id") id: String): Response<Student>

    @POST("students")
    suspend fun createStudent(@Body request: CreateStudentRequest): Response<Student>

    @PATCH("students/{id}")
    suspend fun updateStudent(
        @Path("id") id: String,
        @Body request: UpdateStudentRequest
    ): Response<Student>

    @DELETE("students/{id}")
    suspend fun deleteStudent(@Path("id") id: String): Response<Unit>

    // ===== Classes =====
    @GET("classes")
    suspend fun getClasses(): Response<List<SchoolClass>>

    @POST("classes")
    suspend fun createClass(@Body request: CreateClassRequest): Response<SchoolClass>

    @PATCH("classes/{id}")
    suspend fun updateClass(
        @Path("id") id: String,
        @Body request: UpdateClassRequest
    ): Response<SchoolClass>

    @DELETE("classes/{id}")
    suspend fun deleteClass(@Path("id") id: String): Response<Unit>

    // ===== Ledger =====
    @POST("ledger/record-cash-payment")
    suspend fun recordCashPayment(@Body request: RecordCashPaymentRequest): Response<RecordCashPaymentResponse>

    // ===== Transactions =====
    @GET("transactions")
    suspend fun getTransactions(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("studentId") studentId: String? = null,
        @Query("status") status: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<TransactionListResponse>

    @POST("transactions/{id}/void")
    suspend fun voidTransaction(@Path("id") id: String, @Body request: VoidTransactionRequest): Response<VoidTransactionResponse>

    // ===== Users =====
    @GET("users")
    suspend fun getUsers(): Response<List<User>>

    @POST("users")
    suspend fun createUser(@Body request: CreateUserRequest): Response<User>

    @PATCH("users/{id}")
    suspend fun updateUser(
        @Path("id") id: String,
        @Body request: UpdateUserRequest
    ): Response<User>

    @POST("users/{id}/suspend")
    suspend fun deleteUser(@Path("id") id: String): Response<Unit>

    // ===== Reports =====
    @GET("dashboard/reports/collections")
    suspend fun getRevenueReport(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String
    ): Response<ReportCollection>

    @GET("dashboard/reports/students-debt")
    suspend fun getDebtsReport(): Response<List<DebtStudent>>
}
