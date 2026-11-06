package com.scolapay.mobile

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.scolapay.mobile.data.model.*
import com.scolapay.mobile.data.api.ApiService
import com.scolapay.mobile.util.CurrencyFormatter
import org.junit.Assert.*
import org.junit.Test
import retrofit2.http.PATCH
import retrofit2.http.POST

class ApiContractTest {
    private val gson = Gson()

    @Test fun loginUsesTenantAndCamelCaseToken() {
        val request = gson.toJsonTree(LoginRequest("ECOLE1", "admin@example.test", "password")).asJsonObject
        assertEquals("ECOLE1", request["tenantCode"].asString)
        val response = gson.fromJson("""{"accessToken":"test-token","expiresInSeconds":300,"user":{"id":"cuid-user","tenantId":"cuid-school","fullName":"Admin Test","email":"admin@example.test","role":"TENANT_ADMIN"}}""", LoginResponse::class.java)
        assertEquals("test-token", response.accessToken)
        assertEquals("cuid-user", response.user.id)
    }

    @Test fun cashAmountIsSerializedAsAnExactString() {
        val body = gson.toJsonTree(RecordCashPaymentRequest("cuid-student", "9007199254740993", "stable-key")).asJsonObject
        assertTrue(body["amountCents"].asJsonPrimitive.isString)
        assertEquals("9007199254740993", body["amountCents"].asString)
    }

    @Test fun paginationMatchesApiEnvelope() {
        val page = gson.fromJson("""{"data":[],"meta":{"total":21,"page":1,"limit":20,"totalPages":2}}""", StudentListResponse::class.java)
        assertEquals(2, page.meta.totalPages)
        assertTrue(page.hasMore)
    }

    @Test fun nestedDashboardDoesNotSilentlyBecomeZero() {
        val response = gson.fromJson("""{"students":{"total":12,"active":10,"withDebt":2,"totalDebtCents":"9007199254740993","totalCreditCents":"0"},"transactions":{"total":8,"today":2,"thisMonth":5},"cashCollected":{"todayCents":"100","thisMonthCents":"25000"},"recentTransactions":[]}""", DashboardSummary::class.java)
        assertEquals(10, response.activeStudents)
        assertEquals("90071992547409.93", response.totalDebts.toPlainString())
        assertEquals("250.00", response.totalRevenue.toPlainString())
    }

    @Test fun moneyInputDoesNotRoundOrOverflow() {
        assertEquals("9007199254740993", CurrencyFormatter.parseAmountCents("90071992547409.93"))
        assertEquals("101", CurrencyFormatter.parseAmountCents("1,01"))
        listOf("1.001", "-1", "0", "1e3", "92233720368547758.08", "9".repeat(1000)).forEach {
            assertNull(it, CurrencyFormatter.parseAmountCents(it))
        }
    }

    @Test fun retrofitUsesExistingRoutesAndPatch() {
        val methods = ApiService::class.java.declaredMethods
        assertEquals("students/{id}", methods.single { it.name == "updateStudent" }.getAnnotation(PATCH::class.java).value)
        assertEquals("ledger/record-cash-payment", methods.single { it.name == "recordCashPayment" }.getAnnotation(POST::class.java).value)
    }
}
