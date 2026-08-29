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
    /**
     * Human-readable name of who got paid / who paid, cleaned of the bank's boilerplate.
     * This is the only field the user is ever asked about, so it has to be short and legible.
     */
    val payee: String?,
    /** Raw UPI VPA (`someone@bank`) when the SMS contains one. */
    val vpa: String?,
    /**
     * True when the money went to a person rather than a registered merchant
     * (UPI P2A vs P2M). Drives the "looks like a cab fare" guess.
     */
    val paidToPerson: Boolean,
    val refNo: String?,
    val balance: Double?,
    val bankHint: String?,
    /** The SMS looks like money moving between the user's own accounts, not real income. */
    val selfTransferHint: Boolean,
    /** The SMS looks like a refund/reversal, not real income. */
    val refundHint: Boolean,
    /**
     * Stable identifier for this alert, used to avoid importing the same SMS twice
     * (re-delivered broadcasts, or a backfill run over messages already imported live).
     */
    val dedupeKey: String,
    val rawText: String,
)

/**
 * Best-effort, regex-based parser for common Indian bank transaction SMS formats
 * (debit/credit alerts, UPI notifications). It intentionally ignores the date/time
 * printed inside the SMS body - formats vary too much to parse reliably, and some banks
 * print a time with no AM/PM marker at all - callers should use the SMS's own received
 * timestamp instead.
 *
 * Not a bank-specific parser: it looks for generic "debited"/"credited" phrasing
 * with a nearby amount, so it works across TMB/SBI/HDFC/ICICI/Axis/etc. without
 * per-bank rules, at the cost of occasionally missing unusual formats.
 *
 * Deliberately rule-based rather than model-based: every amount and every category guess
 * has to be explainable when it's wrong, and a wrong number you can't trace is worse than
 * no number at all.
 */
// Guard clauses all the way down: each rule bails out the moment it knows it does not apply.
@Suppress("ReturnCount")
object BankSmsParser {

    private const val MAX_PAYEE_LENGTH = 40
    private const val MAX_PAYEE_WORDS = 6
    private const val LABEL_LOOKBEHIND_CHARS = 20
    private const val MIN_PERSON_NAME_WORDS = 2
    private const val MAX_PERSON_NAME_WORDS = 4
    private const val MIN_MASHED_TOKEN_LENGTH = 12
    private const val MIN_BRAND_PREFIX_LENGTH = 4

    private val ignoredPhrases = listOf(
        "declined", "failed", "reversed", "not successful", "unsuccessful",
        "collect request", "requested rs", "will be debited", "otp", "one time password",
        "e-mandate", "emandate", "has been registered", "is due", "due on"
    )

    private val debitKeywords = "debited|debit|withdrawn|spent|paid"
    private val creditKeywords = "credited|credit|received|deposited"

    /**
     * Words that, when they appear just before an amount, mean the amount is *not* the
     * transaction: available balance, credit limit, etc. A credit-card alert contains the
     * purchase amount, the available limit and the total limit all formatted identically,
     * so "the first amount in the message" is never a safe rule.
     */
    private val nonTransactionAmountLabels = listOf(
        "bal", "balance", "avbl", "avlbl", "avl", "available", "limit",
        "outstanding", "due", "credit"
    )

    /**
     * Filler that can sit between a label and its amount ("Avl Bal is Rs. ") and must be
     * stripped before asking which label the amount actually belongs to.
     */
    private val amountLabelNoiseRegex = Regex(
        "(?:\\s|[:\\-.=]|is|of|inr|rs|₹)+$",
        RegexOption.IGNORE_CASE
    )

    private val forwardAmountRegex = Regex(
        "($debitKeywords|$creditKeywords)\\s+(?:with|by|for)?\\s*" +
            "(?:inr|rs\\.?|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    /**
     * SBI's UPI alerts print the amount with no currency marker at all ("debited by 340.0").
     * Dropping the marker is only safe with two extra anchors: an explicit with/by/for
     * connector, and a decimal part - otherwise a date ("debited on 05...") reads as money.
     */
    private val forwardBareAmountRegex = Regex(
        "($debitKeywords|$creditKeywords)\\s+(?:with|by|for)\\s+([\\d,]+\\.\\d{1,2})",
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
        "(?:upi\\s*)?ref(?:erence)?\\.?\\s*(?:no\\.?|id)?\\s*[:\\-]?\\s*([A-Za-z0-9]{4,})",
        RegexOption.IGNORE_CASE
    )

    private val balanceRegex = Regex(
        "(?:avbl|available)\\.?\\s*bal(?:ance)?\\.?\\s*(?:is)?\\s*[:\\-]?\\s*" +
            "(?:inr|rs\\.?|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    private val bankHintRegex = Regex("-\\s*([A-Za-z]{2,10})\\s*$")

    private val selfTransferPhrases = listOf(
        "own account", "your own", "self transfer", "self-transfer", "to self",
        "sweep", "linked account"
    )

    private val refundPhrases = listOf("refund", "reversal", "chargeback", "cashback")

    // ---------------------------------------------------------------------------------------
    // Payee extraction
    // ---------------------------------------------------------------------------------------

    /**
     * The UPI reference run, e.g. `UPI/P2M/141159140296/K MANIKANTA`. Handled before the
     * "to"/"at"/"Info:" patterns because the name lives in the last slash-segment rather than
     * after a preposition.
     */
    private val upiReferenceRegex = Regex("\\bupi/[A-Za-z0-9/@.\\-_ ]+", RegexOption.IGNORE_CASE)

    /** Slash-segments that are UPI plumbing rather than somebody's name. */
    private val upiSegmentCodes = setOf(
        "upi", "p2m", "p2a", "p2p", "dr", "cr", "neft", "imps", "ach", "mob",
        "col", "pay", "payment", "na"
    )

    /**
     * Ordered: the first pattern that matches wins, most specific first. Group 1 is the payee.
     */
    private val payeePatterns: List<Regex> = listOf(
        // "trf to JOHN DOE", "transferred to JOHN DOE"
        Regex("\\b(?:trf|transfer(?:red)?)\\s+to\\s+(.+)", RegexOption.IGNORE_CASE),
        // "to VPA someone@bank JOHN DOE"
        Regex("\\bto\\s+vpa\\s+(.+)", RegexOption.IGNORE_CASE),
        // SBI-style "Info: UPI/P2M/141159140296/K MANIKANTA"
        Regex("\\binfo\\s*[:\\-]\\s*(.+)", RegexOption.IGNORE_CASE),
        // "spent at ZOMATO", "at BIG BAZAAR"
        Regex("\\bat\\s+(.+)", RegexOption.IGNORE_CASE),
        // "credited by JOHN DOE", "from JOHN DOE"
        Regex("\\bfrom\\s+(.+)", RegexOption.IGNORE_CASE),
        // Generic "to JOHN DOE" - last because "to" appears in a lot of boilerplate
        Regex("\\bto\\s+(.+)", RegexOption.IGNORE_CASE),
    )

    /**
     * Phrases that begin the bank's footer. Everything from the first one onwards is dropped.
     *
     * This exists because the alert body is a single run-on sentence: without an explicit cut
     * list, a payee like "HARIPRIYA VELLODI" comes out as the name plus the entire
     * "IF THIS TRANSACTION WAS NOT INITIATED BY YOU..." footer glued to it. Stopping at
     * "two or more spaces" does not work - whitespace is normalised before this runs, so that
     * condition can never be true.
     */
    private val payeeFooterPhrases = listOf(
        "if this", "if not", "if you did not", "not you", "to block", "block",
        "call", "sms", "report", "dispute", "download", "click", "visit", "log on",
        "customer id", "helpline", "toll free", "thank you", "regards",
        "ref no", "refno", "ref", "reference", "txn id", "transaction id",
        "avl bal", "avbl bal", "available bal", "bal", "balance", "a/c", "ac no", "account",
        "on", "at", "upi", "credited", "debited", "info", "towards", "linked",
        "using", "via", "through", "thru", "your", "own", "self"
    )

    /**
     * Matched with word boundaries, never as a bare substring: `indexOf("on ")` happily fires
     * inside "AMAZON SELLER" and hands back "AMAZ".
     */
    private val payeeFooterRegex = Regex(
        payeeFooterPhrases.joinToString(separator = "|", prefix = "\\b(?:", postfix = ")\\b") {
            Regex.escape(it)
        },
        RegexOption.IGNORE_CASE
    )

    /**
     * Punctuation that ends the name even mid-phrase. A full stop only counts when it is
     * followed by whitespace or end-of-string, so initials like "K.MANIKANTA" survive.
     */
    private val payeeTerminatorRegex = Regex("[,;|(\\[!?:]|\\.(?=\\s|$)")

    private val merchantMarkers = listOf(
        "pvt", "ltd", "limited", "llp", "inc", "corp", "store", "stores", "mart",
        "retail", "services", "solutions", "technologies", "enterprises", "traders",
        "restaurant", "hotel", "cafe", "foods", "pharmacy", "medicals", "petro", "fuels"
    )

    private val moneyHintRegex = Regex(
        "(?:inr|rs\\.?|₹)\\s*\\d|" +
            "\\b(?:debited|credited|withdrawn|spent|deposited|transferred|upi)\\b",
        RegexOption.IGNORE_CASE
    )

    /**
     * Wider than [parse] on purpose: "this message is about money" rather than "I can parse
     * this message". The diagnostic uses the gap between the two to show which alerts the
     * rules are missing - a filter that is too narrow doesn't report no data, it reports
     * confident wrong data.
     */
    fun looksLikeMoneyAlert(smsBody: String): Boolean = moneyHintRegex.containsMatchIn(smsBody)

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

        val vpa = vpaRegex.find(text)?.value
        val payee = extractPayee(text, vpa)

        return ParsedBankSms(
            type = type,
            amount = amount,
            accountSuffix = accountSuffixRegex.find(text)?.groupValues?.get(1),
            payee = payee,
            vpa = vpa,
            paidToPerson = looksLikePersonPayment(lower, payee),
            refNo = refNoRegex.find(text)?.groupValues?.get(1),
            balance = balanceRegex.find(text)?.groupValues?.get(1)
                ?.replace(",", "")?.toDoubleOrNull(),
            bankHint = bankHintRegex.find(text)?.groupValues?.get(1),
            selfTransferHint = selfTransferPhrases.any { lower.contains(it) },
            refundHint = refundPhrases.any { lower.contains(it) },
            dedupeKey = dedupeKeyOf(text),
            rawText = text,
        )
    }

    /**
     * A stable key for "this exact alert". Prefers the bank's own reference number; falls back
     * to a hash of the normalised body, which is safe because every alert carries its own
     * date/time and reference, so two genuinely different payments never hash the same.
     */
    private fun dedupeKeyOf(text: String): String {
        refNoRegex.find(text)?.groupValues?.get(1)?.let { return "ref-${it.lowercase()}" }
        val normalized = text.lowercase().replace(Regex("\\s+"), " ").trim()
        return "txt-" + Integer.toHexString(normalized.hashCode())
    }

    private fun matchDirectionAndAmount(text: String): Pair<String, String>? {
        listOf(forwardAmountRegex, forwardBareAmountRegex).forEach { regex ->
            regex.findAll(text)
                .firstOrNull { !amountBelongsToAnotherLabel(text, it, group = 2) }
                ?.let { return it.groupValues[1] to it.groupValues[2] }
        }

        reverseAmountRegex.findAll(text)
            .firstOrNull { !amountBelongsToAnotherLabel(text, it, group = 1) }
            ?.let { return it.groupValues[2] to it.groupValues[1] }

        return null
    }

    /**
     * Guards the "Avl Bal Rs 1,00,000" case. A credit card alert carries the purchase amount,
     * the available limit and the total limit in identical formatting, so an amount is only
     * the transaction amount if the label immediately in front of it isn't a balance or a
     * limit. Anchoring to the label - never to position in the message - is what stops a
     * template reshuffle from silently logging a credit limit as a purchase.
     */
    private fun amountBelongsToAnotherLabel(
        text: String,
        match: MatchResult,
        group: Int,
    ): Boolean {
        val amountStart = match.groups[group]?.range?.first ?: return false
        val from = (amountStart - LABEL_LOOKBEHIND_CHARS).coerceAtLeast(0)
        val window = amountLabelNoiseRegex.replace(text.substring(from, amountStart).lowercase(), "")
        return nonTransactionAmountLabels.any { window.endsWith(it) }
    }

    /**
     * Re-runs the payee tidy-up on a name that is already stored on a transaction.
     *
     * Parsing improvements only help messages that arrive afterwards; everything captured
     * before the fix keeps whatever the parser produced at the time. Rewriting those rows in
     * the database would be a migration that can only be got wrong once, so screens that show
     * an auto-imported name run it through here instead and the history reads correctly
     * without anything being rewritten.
     */
    fun readablePayee(stored: String?): String? =
        stored?.takeIf { it.isNotBlank() }?.let { capPayee(it) } ?: stored

    private fun extractPayee(text: String, vpa: String?): String? {
        val normalized = text.replace(Regex("\\s+"), " ")
        upiReferencePayee(normalized)?.let { return it }
        for (pattern in payeePatterns) {
            val candidate = pattern.find(normalized)?.groupValues?.get(1) ?: continue
            val cleaned = cleanPayee(candidate)
            if (cleaned != null) return cleaned
        }
        // Nothing named the payee, but a VPA at least identifies them consistently.
        return vpa?.substringBefore("@")?.let { cleanPayee(it) }
    }

    private fun upiReferencePayee(text: String): String? {
        val reference = cutAtTerminator(upiReferenceRegex.find(text)?.value ?: return null)
        val name = reference.split('/')
            .map { it.trim() }
            .lastOrNull { segment ->
                segment.any { it.isLetter() } &&
                    segment.lowercase() !in upiSegmentCodes &&
                    !segment.contains('@')
            } ?: return null
        return capPayee(name)
    }

    /**
     * Turns a raw run of text following "to"/"at"/"Info:" into something a human can read on a
     * card: footer removed, UPI plumbing removed, length capped.
     */
    private fun cleanPayee(raw: String): String? {
        var value = cutAtFooter(raw.trim()) ?: return null
        value = cutAtTerminator(value).trim().trim('-', '*', '#', '/', '"', '\'')

        // "someone@okhdfcbank" -> "someone"
        if (value.contains('@')) value = value.substringBefore('@')

        return capPayee(value)
    }

    /**
     * The last line of defence against a paragraph ending up where a name belongs. Truncating
     * this in the UI instead would hide broken data behind an ellipsis rather than fix it.
     */
    private fun capPayee(raw: String): String? {
        // A UPI handle or a NEFT beneficiary line resolves to a real name here, so the queue
        // groups by the merchant rather than by the terminal that happened to take the money.
        PayeeNames.readable(raw)?.let { named ->
            if (named != raw) return named
        }

        var value = raw.split(' ').filter { it.isNotBlank() }
            .take(MAX_PAYEE_WORDS)
            .mapNotNull(::simplifyMashedToken)
            .joinToString(" ")
        if (value.length > MAX_PAYEE_LENGTH) value = value.take(MAX_PAYEE_LENGTH).trim()

        // A payee with no letters is a reference number that slipped through, not a name.
        if (value.none { it.isLetter() }) return null
        return value.ifBlank { null }
    }

    /**
     * Recovers the brand from a merchant token that has a transaction id welded to it.
     *
     * QR-code aggregators put the whole acquirer reference in the payee field, so the alert
     * names you "BHARATPE9O7A7B2M0F2X04941" rather than "BHARATPE". A name you cannot read is
     * a name you cannot sort by, and the sorting queue groups by payee - left alone, every
     * single BharatPe payment becomes its own one-off entry that never learns anything.
     *
     * Only long letter-and-digit mashes are touched. Ordinary names survive untouched, digits
     * and all: "AMAZON SELLER SERVICES" and "K MANIKANTA" have no digits, and a short token
     * like "SWIGGY24" is under the length floor.
     */
    private fun simplifyMashedToken(token: String): String? {
        if (token.length < MIN_MASHED_TOKEN_LENGTH) return token
        if (token.none(Char::isDigit) || token.none(Char::isLetter)) return token

        val brand = token.takeWhile(Char::isLetter)
        // Too little leading text to be a brand - the token is a bare reference, so drop it
        // rather than surface a fragment.
        return brand.takeIf { it.length >= MIN_BRAND_PREFIX_LENGTH }
    }

    /**
     * Returns null when the candidate *starts* with footer text - that means the pattern
     * matched boilerplate rather than a name, and there is no payee to salvage.
     */
    private fun cutAtFooter(value: String): String? {
        val cut = payeeFooterRegex.find(value)?.range?.first ?: return value
        if (cut == 0) return null
        return value.substring(0, cut)
    }

    private fun cutAtTerminator(value: String): String {
        val cut = payeeTerminatorRegex.find(value)?.range?.first?.takeIf { it > 0 }
        return if (cut != null) value.substring(0, cut) else value
    }

    /**
     * UPI tags merchant payments as P2M and person payments as P2A, but plenty of alerts carry
     * neither tag - so fall back to the shape of the name. A short all-alphabetic name with no
     * company suffix is almost always a person.
     */
    private fun looksLikePersonPayment(lowerText: String, payee: String?): Boolean {
        if (lowerText.contains("p2m")) return false
        if (lowerText.contains("p2a") || lowerText.contains("/p2p/")) return true
        // Card rails only reach registered merchants - nobody pays an auto driver by card.
        if (lowerText.contains("card")) return false

        val name = payee?.lowercase() ?: return false
        if (merchantMarkers.any { name.contains(it) }) return false

        // Two-plus name parts, letters only. "K.MANIKANTA" counts (initials split on the dot);
        // a single all-caps token like "ZOMATO" is a brand, not somebody's name.
        val words = name.split(' ', '.').filter { it.isNotBlank() }
        if (words.size !in MIN_PERSON_NAME_WORDS..MAX_PERSON_NAME_WORDS) return false
        return words.all { word -> word.all(Char::isLetter) }
    }
}
