package com.ivy.domain.usecase.sms

/**
 * Best-effort guess of a category *name* from an auto-imported SMS transaction.
 *
 * This never creates categories - it only returns a name, and the caller is
 * expected to look up an existing category with a matching name (case-insensitive).
 * If nothing matches, the transaction is left uncategorized rather than guessing wrong.
 */
object SmsCategoryGuesser {

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

    fun guess(rawText: String, counterparty: String?): String? {
        val haystack = listOfNotNull(counterparty, rawText).joinToString(" ").lowercase()
        return keywordToCategory.firstOrNull { (keywords, _) ->
            keywords.any { haystack.contains(it) }
        }?.second
    }
}
