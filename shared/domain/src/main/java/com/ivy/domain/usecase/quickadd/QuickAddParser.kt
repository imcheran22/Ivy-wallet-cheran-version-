package com.ivy.domain.usecase.quickadd

import com.ivy.base.model.TransactionType

/**
 * Reads what someone types into the quick-add notification.
 *
 * The box is one line on a lock screen, typed one-handed, so the grammar has to be whatever a
 * person would write without being taught: an amount and, usually, what it was for. "250
 * coffee", "coffee 250", "1,250.50 groceries" and a bare "40" all have to work.
 *
 * The rules are deliberately few. The first number is the amount - a title with digits in it
 * ("2 coffees") is rarer than an amount, and mis-reading the amount is the failure that
 * matters. Everything left over, once the amount and any currency symbol is taken out, is the
 * title. A leading "+" marks income, because the notification is used far more for spending
 * and a prefix costs one character.
 */
object QuickAddParser {

    data class Parsed(
        val amount: Double,
        val title: String?,
        val type: TransactionType,
    )

    private val amountRegex = Regex("""\d+(?:[,\d]*\d)?(?:\.\d+)?""")

    fun parse(raw: String): Parsed? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val income = trimmed.startsWith("+")
        val body = trimmed.removePrefix("+").removePrefix("-").trim()

        val match = amountRegex.find(body) ?: return null
        val amount = match.value.replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val title = (body.removeRange(match.range))
            .trim()
            .trim('-', ':', ',')
            .trim()
            .takeIf { it.isNotEmpty() && it.length <= MAX_TITLE }

        return Parsed(
            amount = amount,
            title = title,
            type = if (income) TransactionType.INCOME else TransactionType.EXPENSE,
        )
    }

    private const val MAX_TITLE = 120
}
