package com.ivy.domain.usecase.sms

import com.ivy.base.model.TransactionType

/**
 * A guessed category together with the reason it was guessed.
 *
 * The reason is not decoration: a suggestion you can't audit is worse than no suggestion,
 * because you stop checking it. Every guess this file makes has to be explainable in one
 * short line shown next to the pre-selected category.
 */
data class SmsCategoryGuess(
    val categoryName: String,
    val reason: String,
)

/**
 * Best-effort guess of a category *name* for an auto-imported SMS transaction.
 *
 * This never creates categories - it only returns a name, and the caller is expected to look
 * up an existing category with a matching name (case-insensitive). If nothing matches, the
 * transaction is left uncategorized rather than guessing wrong.
 *
 * Plain rules, deliberately. A rule can be read, debugged and explained when it's wrong.
 */
@Suppress("ReturnCount")
object SmsCategoryGuesser {

    /** Fares below this are usually a chai shop, above it usually not an auto. */
    private const val MIN_FARE_AMOUNT = 40.0
    private const val MAX_FARE_AMOUNT = 600.0

    private val keywordToCategory: List<Pair<List<String>, String>> = listOf(
        listOf("rapido", "ola", "uber", "meru", "taxi") to "Transport",
        listOf("swiggy", "zomato", "dominos", "mcdonald", "kfc", "pizza", "restaurant") to "Food",
        listOf("amazon", "flipkart", "myntra", "ajio", "meesho") to "Shopping",
        listOf("netflix", "spotify", "hotstar", "prime video", "sonyliv", "youtube premium") to "Entertainment",
        listOf(
            "electricity", "eb board", "water board", "gas agency",
            "broadband", "airtel", "jio", "vodafone", "vi ", "bsnl"
        ) to "Utilities",
        listOf("atm", "cash withdrawal") to "Cash",
        listOf("salary", "payroll") to "Salary",
        listOf("rent") to "Rent",
        listOf("insurance", "lic ") to "Insurance",
        listOf("hospital", "pharmacy", "medical", "clinic", "apollo", "practo") to "Health",
        listOf("petrol", "diesel", "fuel", "hpcl", "ioc", "bpcl") to "Fuel",
    )

    fun guess(parsed: ParsedBankSms): SmsCategoryGuess? {
        incomeGuess(parsed)?.let { return it }
        val haystack = listOfNotNull(parsed.payee, parsed.vpa, parsed.rawText)
            .joinToString(" ").lowercase()
        keywordGuess(haystack)?.let { return it }
        return fareGuess(
            type = parsed.type,
            amount = parsed.amount,
            paidToPerson = parsed.paidToPerson,
        )
    }

    /**
     * Re-derives a suggestion for a transaction that is already in the wallet, from the little
     * that survives an import: the payee, the amount, and whether the money went to a person.
     * The raw SMS is deliberately not stored, so this sees less than [guess] does.
     */
    fun guessFromRecord(
        payee: String?,
        amount: Double,
        type: TransactionType,
        paidToPerson: Boolean,
    ): SmsCategoryGuess? {
        payee?.lowercase()?.let { keywordGuess(it) }?.let { return it }
        return fareGuess(type = type, amount = amount, paidToPerson = paidToPerson)
    }

    /**
     * Money coming in is the case where a wrong guess does real damage: a transfer between the
     * user's own accounts and a refund both look exactly like income, and counting either as
     * earnings turns the number into fiction.
     */
    private fun incomeGuess(parsed: ParsedBankSms): SmsCategoryGuess? {
        if (parsed.type != TransactionType.INCOME) return null
        return when {
            parsed.selfTransferHint -> SmsCategoryGuess(
                categoryName = SmsCategories.MOVED_BETWEEN_ACCOUNTS,
                reason = "the alert mentions your own account - not earnings",
            )

            parsed.refundHint -> SmsCategoryGuess(
                categoryName = SmsCategories.REFUND,
                reason = "the alert says refund or reversal - not earnings",
            )

            else -> null
        }
    }

    private fun keywordGuess(haystack: String): SmsCategoryGuess? {
        val match = keywordToCategory.firstNotNullOfOrNull { (keywords, category) ->
            keywords.firstOrNull { haystack.contains(it) }?.let { it to category }
        } ?: return null
        val (keyword, category) = match
        return SmsCategoryGuess(
            categoryName = category,
            reason = "the alert mentions \"${keyword.trim()}\"",
        )
    }

    /**
     * The auto-driver case. A different person's UPI every single time, so remembering past
     * payees is worthless here - the only signal left is the shape of the payment.
     */
    private fun fareGuess(
        type: TransactionType,
        amount: Double,
        paidToPerson: Boolean,
    ): SmsCategoryGuess? {
        if (type != TransactionType.EXPENSE) return null
        if (!paidToPerson) return null
        if (amount < MIN_FARE_AMOUNT || amount > MAX_FARE_AMOUNT) return null
        return SmsCategoryGuess(
            categoryName = SmsCategories.CABS_AND_AUTOS,
            reason = "paid to a person, between ₹${MIN_FARE_AMOUNT.toInt()} and " +
                "₹${MAX_FARE_AMOUNT.toInt()} - a typical fare",
        )
    }
}
