package com.ivy.domain.usecase.sms

/**
 * Turns the identifier a UPI alert carries into something worth reading on a card.
 *
 * Indian bank SMS almost never name the shop. What they carry is the payee's VPA handle -
 * `rapido522347.rzp@axisbank`, `swiggydinein@icici`, `BHARATPE9O7A7B2M0F2X04941@yesbank` - which
 * is a routing address with a brand buried in it, an acquirer reference, or a person's own
 * handle. Rendered raw it is unreadable, and worse, unsortable: two payments to the same
 * merchant through different terminals look like two different payees and never learn a
 * category between them.
 *
 * Three passes, in order of how much can be claimed honestly:
 *
 *  1. A known brand inside the handle wins, because that is a name the user recognises and it
 *     collapses every terminal of that merchant onto one payee.
 *  2. Failing that, a QR-acquirer prefix at least names the rail - "BharatPe QR" is not the
 *     shop, but it tells the user what kind of payment they are looking at instead of showing
 *     them a hex string.
 *  3. Otherwise the handle is tidied and kept. A person's handle is not a name, but it is
 *     stable, so naming it once still teaches every future payment to them.
 */
object PayeeNames {

    /**
     * Payment-service suffixes welded onto a merchant handle. Stripping them is what turns
     * `rapido522347.rzp` into something a brand can be recognised in.
     */
    private val pspSuffixes = listOf(
        ".rzp", ".payu", ".payswiff", ".hypg", ".ccav", ".pinelabs", ".mswipe",
        ".ezetap", ".worldline", ".ncl.brk", ".brk", ".pz", ".cred", ".jupiteraxis",
        ".ybl", ".ibl", ".axl", ".apl", ".upi", ".okaxis", ".oksbi", ".okhdfcbank",
        ".okicici", ".paytm", ".airtel", ".freecharge", ".yapl", ".waaxis", ".wasbi",
    )

    /**
     * Brands worth recognising, longest first so "flipkartgiftcard" resolves to Flipkart
     * rather than stopping at a shorter accidental match.
     */
    private val brands: List<Pair<String, String>> = listOf(
        "flipkartgiftcard" to "Flipkart",
        "chalomobility" to "Chalo",
        "googlecloud" to "Google Cloud",
        "railsbiupi" to "IRCTC Rail",
        "delhivery" to "Delhivery",
        "zerodhabroking" to "Zerodha",
        "districtdining" to "District Dining",
        "district" to "District",
        "eternallimited" to "Eternal",
        "swiggydinein" to "Swiggy",
        "swiggyinstamart" to "Swiggy Instamart",
        "pvrinox" to "PVR INOX",
        "bigbasket" to "BigBasket",
        "dominos" to "Domino's",
        "flipkart" to "Flipkart",
        "zomato" to "Zomato",
        "swiggy" to "Swiggy",
        "rapido" to "Rapido",
        "zerodha" to "Zerodha",
        "blinkit" to "Blinkit",
        "myntra" to "Myntra",
        "amazon" to "Amazon",
        "payswiff" to "Payswiff",
        "irctc" to "IRCTC",
        "meesho" to "Meesho",
        "zepto" to "Zepto",
        "dunzo" to "Dunzo",
        "groww" to "Groww",
        "uber" to "Uber",
        "ola" to "Ola",
        "jio" to "Jio",
        "airtel" to "Airtel",
        "nykaa" to "Nykaa",
        "lenskart" to "Lenskart",
        "decathlon" to "Decathlon",
        "starbucks" to "Starbucks",
        "mcdonalds" to "McDonald's",
        "kfc" to "KFC",
        "indianoil" to "Indian Oil",
        "bharatpetroleum" to "Bharat Petroleum",
        "hpcl" to "HPCL",
    )

    /**
     * QR acquirers. The handle after these is the terminal's own reference, never a shop name,
     * so the honest answer is the rail plus nothing.
     */
    private val qrRails: List<Pair<Regex, String>> = listOf(
        Regex("^bharatpe", RegexOption.IGNORE_CASE) to "BharatPe QR",
        Regex("^paytmqr", RegexOption.IGNORE_CASE) to "Paytm QR",
        Regex("^paytm[.\\-]", RegexOption.IGNORE_CASE) to "Paytm QR",
        Regex("^gpay-", RegexOption.IGNORE_CASE) to "Google Pay QR",
        Regex("^q\\d{6,}$", RegexOption.IGNORE_CASE) to "Paytm QR",
        Regex("^sbipmopad", RegexOption.IGNORE_CASE) to "SBI QR",
        Regex("^vyapar", RegexOption.IGNORE_CASE) to "Vyapar QR",
    )

    /**
     * `NEFT-UTIB0000009-MANIPAL HEALTH ENTER-AX` - a bank transfer where the middle segment is
     * the beneficiary the bank actually printed, which is the one case where the alert does
     * name a counterparty properly.
     */
    private val neftRegex = Regex(
        "^(?:NEFT|RTGS|IMPS)-[A-Z]{4}[0-9A-Z]{5,}-(.+?)(?:-[A-Z]{2})?$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * @return a readable name, or null when the token carries no usable identity at all.
     */
    fun readable(raw: String?): String? {
        val token = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        neftRegex.find(token)?.groupValues?.getOrNull(1)?.let { beneficiary ->
            return titleCase(beneficiary.trim())
        }

        val handle = token.substringBefore('@').trim()
        if (handle.isEmpty()) return null

        val stripped = stripPsp(handle)

        brandIn(stripped)?.let { return it }
        qrRails.firstOrNull { (pattern, _) -> pattern.containsMatchIn(handle) }
            ?.let { return it.second }

        return tidy(stripped) ?: tidy(handle)
    }

    /**
     * Suffixes are matched case-insensitively but cut off the original string, so a handle
     * that turns out not to be a known brand keeps the capitalisation the bank sent -
     * "ICIC bank" should not come back as "icic bank".
     */
    private fun stripPsp(handle: String): String {
        var value = handle
        var changed = true
        while (changed) {
            changed = false
            for (suffix in pspSuffixes) {
                if (value.length > suffix.length && value.endsWith(suffix, ignoreCase = true)) {
                    value = value.dropLast(suffix.length)
                    changed = true
                }
            }
        }
        return value
    }

    private fun brandIn(handle: String): String? {
        val flat = handle.filter { it.isLetterOrDigit() }.lowercase()
        return brands.firstOrNull { (needle, _) -> flat.contains(needle) }?.second
    }

    /**
     * A handle with the machine parts taken off: the trailing terminal digits and the
     * separators that held them on. What is left is either a person's chosen handle or
     * nothing, and nothing is the more useful answer of the two.
     */
    private fun tidy(handle: String): String? {
        val withoutTrailingDigits = handle
            .trimEnd { it.isDigit() || it == '-' || it == '.' || it == '_' }
            .trim('-', '.', '_')

        val letters = withoutTrailingDigits.count(Char::isLetter)
        if (letters < MIN_LETTERS) return null

        // A long letter-and-digit mash is an acquirer reference wearing a handle's clothes.
        if (withoutTrailingDigits.length >= MASH_LENGTH &&
            withoutTrailingDigits.any(Char::isDigit) &&
            withoutTrailingDigits.none { it == '.' || it == '-' || it == '_' }
        ) {
            return null
        }

        return withoutTrailingDigits
    }

    private fun titleCase(value: String): String = value.split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }

    private const val MIN_LETTERS = 3
    private const val MASH_LENGTH = 14
}
