package com.educash.mobile.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    private val format = NumberFormat.getNumberInstance(Locale("fr", "FR")).apply {
        isGroupingUsed = true
    }

    fun formatCents(cents: Int): String {
        val amount = cents / 100.0
        return "${format.format(amount)} ${Constants.DEFAULT_CURRENCY}"
    }

    fun formatCents(cents: Long): String {
        val amount = cents / 100.0
        return "${format.format(amount)} ${Constants.DEFAULT_CURRENCY}"
    }

    fun formatCents(cents: String): String {
        val intValue = cents.toLongOrNull() ?: 0L
        return formatCents(intValue)
    }

    fun parseAmount(amount: Double): Int = (amount * 100).toInt()
}
