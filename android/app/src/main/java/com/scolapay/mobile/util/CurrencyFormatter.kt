package com.scolapay.mobile.util

import java.text.NumberFormat
import java.util.Locale
import java.math.BigDecimal

object CurrencyFormatter {
    private val format = NumberFormat.getNumberInstance(Locale("fr", "FR")).apply {
        isGroupingUsed = true
    }

    fun formatCents(cents: Int): String {
        val amount = cents / 100.0
        return "${format.format(amount)} ${Constants.DEFAULT_CURRENCY}"
    }

    fun formatCents(cents: Long): String {
        val amount = cents.toBigDecimal().movePointLeft(2)
        return "${format.format(amount)} ${Constants.DEFAULT_CURRENCY}"
    }

    fun formatCents(cents: String): String {
        val intValue = cents.toLongOrNull() ?: 0L
        return formatCents(intValue)
    }

    fun format(amount: BigDecimal): String = "${format.format(amount)} ${Constants.DEFAULT_CURRENCY}"

    fun parseAmountCents(amount: String): String? {
        if (amount.length > 22 || !Regex("^[0-9]+([.,][0-9]{1,2})?$").matches(amount)) return null
        val cents = amount.replace(',', '.').toBigDecimal().movePointRight(2).toBigIntegerExact()
        if (cents.signum() <= 0 || cents > java.math.BigInteger.valueOf(Long.MAX_VALUE)) return null
        return cents.toString()
    }
}
