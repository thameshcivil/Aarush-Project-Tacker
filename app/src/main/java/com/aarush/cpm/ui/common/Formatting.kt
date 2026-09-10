package com.aarush.cpm.ui.common

import java.text.NumberFormat
import java.util.Locale

private val inrFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

fun formatCurrency(amount: Double): String = inrFormat.format(amount)

fun formatQuantity(qty: Double, unit: String): String {
    val trimmed = if (qty == qty.toLong().toDouble()) qty.toLong().toString() else "%.2f".format(qty)
    return if (unit.isBlank()) trimmed else "$trimmed $unit"
}

fun formatPercent(value: Double): String = "%.1f%%".format(value)

private val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())

fun formatDate(epochMillis: Long): String = dateFormat.format(java.util.Date(epochMillis))
