package com.ivy.domain.usecase.sms

import com.ivy.base.model.TransactionType

/**
 * Result of successfully parsing a bank transaction SMS.
 *
 * Only [TransactionType.INCOME] and [TransactionType.EXPENSE] are ever produced here -
 * a single SMS doesn't give us enough signal to safely infer a TRANSFER.
 */
data class ParsedBankSms(
    val type: TransactionType,
    val amount: Double,
    val accountSuffix: String?,
    val counterparty: String?,
    val refNo: String?,
    val balance: Double?,
    val bankHint: String?,
    val rawText: String,
)

/**
 * Best-effort, regex-based parser for common Indian bank transaction SMS formats
 * (debit/credit alerts, UPI notifications). It intentionally ignores the date/time
 * printed inside the SMS body - formats vary too much to parse reliably - callers
 * should use the SMS's own received timestamp instead.
 *
 * Not a bank-specific parser: it looks for generic "debited"/"credited" phrasing
 * with a nearby amount, so it works across TMB/SBI/HDFC/ICICI/Axis/etc. without
 * per-bank rules, at the cost of occasionally missing unusual formats.
 */
object BankSmsParser {

    private val ignoredPhrases = listOf(
        "declined", "failed", "reversed", "not successful", "unsuccessful",
        "collect request", "requested rs", "will be debited", "otp", "one time password"
    )

    private val debitKeywords = "debited|debit|withdrawn|spent|paid"
    private val creditKeywords = "credited|credit|received|deposited"

    private val forwardAmountRegex = Regex(
        "($debitKeywords|$creditKeywords)\\s+(?:with|by|for)?\\s*" +
            "(?:inr|rs\\.?|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    private val reverseAmountRegex = Regex(
        "(?:inr|rs\\.?|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)\\D{0,25}?($debitKeywords|$creditKeywords)",
        RegexOption.IGNORE_CASE
    )

    private val accountSuffixRegex = Regex(
        "a/?c\\.?\\s*(?:no\\.?)?\\s*[x*]*\\s?(\\d{3,6})\\b",
        RegexOption.IGNORE_CASE
    )

    private val vpaRegex = Regex("[a-zA-Z0-9.\\-_]{2,}@[a-zA-Z]{2,}")

    private val refNoRegex = Regex(
        "(?:upi\\s*)?ref(?:erence)?\\.?\\s*no\\.?\\s*[:\\-]?\\s*([A-Za-z0-9]+)",
        RegexOption.IGNORE_CASE
    )

    private val balanceRegex = Regex(
        "(?:avbl|available)\\.?\\s*bal(?:ance)?\\.?\\s*(?:is)?\\s*[:\\-]?\\s*" +
            "(?:inr|rs\\.?|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    private val bankHintRegex = Regex("-\\s*([A-Za-z]{2,10})\\s*$")

    fun parse(smsBody: String): ParsedBankSms? {
        val text = smsBody.trim()
        if (text.isBlank()) return null

        val lower = text.lowercase()
        if (ignoredPhrases.any { lower.contains(it) }) return null

        val (direction, amountRaw) = matchDirectionAndAmount(text) ?: return null
        val amount = amountRaw.replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val type = when {
            direction.contains("debit", ignoreCase = true) ||
                direction.equals("withdrawn", ignoreCase = true) ||
                direction.equals("spent", ignoreCase = true) ||
                direction.equals("paid", ignoreCase = true) -> TransactionType.EXPENSE

            else -> TransactionType.INCOME
        }

        val accountSuffix = accountSuffixRegex.find(text)?.groupValues?.get(1)
        val counterparty = vpaRegex.find(text)?.value
        val refNo = refNoRegex.find(text)?.groupValues?.get(1)
        val balance = balanceRegex.find(text)?.groupValues?.get(1)
            ?.replace(",", "")?.toDoubleOrNull()
        val bankHint = bankHintRegex.find(text)?.groupValues?.get(1)

        return ParsedBankSms(
            type = type,
            amount = amount,
            accountSuffix = accountSuffix,
            counterparty = counterparty,
            refNo = refNo,
            balance = balance,
            bankHint = bankHint,
            rawText = text,
        )
    }

    private fun matchDirectionAndAmount(text: String): Pair<String, String>? {
        forwardAmountRegex.find(text)?.let {
            return it.groupValues[1] to it.groupValues[2]
        }
        reverseAmountRegex.find(text)?.let {
            return it.groupValues[2] to it.groupValues[1]
        }
        return null
    }
}
