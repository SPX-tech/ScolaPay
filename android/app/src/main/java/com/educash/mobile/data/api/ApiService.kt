package com.educash.mobile.data.api

import com.educash.mobile.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ===== Auth =====
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // ===== Dashboard =====
    @GET("dashboard/summary")
    suspend fun getDashboardSummary(): Response<DashboardSummary>

    @GET("dashboard/monthly-report")
    suspend fun getMonthlyReport(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Response<MonthlyReport>

    @GET("dashboard/class-report/{classId}")
    suspend fun getClassReport(@Path("classId") classId: Long): Response<ClassReport>

    @GET("dashboard/student-debts")
    suspend fun getStudentDebts(): Response<List<StudentDebt>>

    // ===== Students =====
    @GET("students")
    suspend fun getStudents(
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 50,
        @Query("search") search: String? = null,
        @Query("classId") classId: Long? = null,
        @Query("status") status: String? = null
    ): Response<StudentListResponse>

    @GET("students/{id}")
    suspend fun getStudent(@Path("id") id: Long): Response<Student>

    @POST("students")
    suspend fun createStudent(@Body request: CreateStudentRequest): Response<Student>

    @PUT("students/{id}")
    suspend fun updateStudent(
        @Path("id") id: Long,
        @Body request: UpdateStudentRequest
    ): Response<Student>

    @DELETE("students/{id}")
    suspend fun deleteStudent(@Path("id") id: Long): Response<Unit>

    // ===== Classes =====
    @GET("classes")
    suspend fun getClasses(): Response<List<SchoolClass>>

    @POST("classes")
    suspend fun createClass(@Body request: CreateClassRequest): Response<SchoolClass>

    @PUT("classes/{id}")
    suspend fun updateClass(
        @Path("id") id: Long,
        @Body request: UpdateClassRequest
    ): Response<SchoolClass>

    @DELETE("classes/{id}")
    suspend fun deleteClass(@Path("id") id: Long): Response<Unit>

    // ===== Ledger =====
    @POST("ledger/cash-payment")
    suspend fun recordCashPayment(@Body request: RecordCashPaymentRequest): Response<Transaction>

    // ===== Transactions =====
    @GET("transactions")
    suspend fun getTransactions(
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 50,
        @Query("studentId") studentId: Long? = null,
        @Query("status") status: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<TransactionListResponse>

    @POST("transactions/{id}/void")
    suspend fun voidTransaction(@Path("id") id: Long): Response<Transaction>

    // ===== Users =====
    @GET("users")
    suspend fun getUsers(): Response<List<User>>

    @POST("users")
    suspend fun createUser(@Body request: CreateUserRequest): Response<User>

    @PUT("users/{id}")
    suspend fun updateUser(
        @Path("id") id: Long,
        @Body request: UpdateUserRequest
    ): Response<User>

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: Long): Response<Unit>

    // ===== Reports =====
    @GET("reports/revenue")
    suspend fun getRevenueReport(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String
    ): Response<RevenueReport>

    @GET("reports/student-debts")
    suspend fun getDebtsReport(
        @Query("classId") classId: Long? = null
    ): Response<List<StudentDebt>>
}
