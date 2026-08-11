package com.ivy.domain.usecase.sms

/**
 * The marker written into an auto-imported transaction's description, and read back to find
 * those transactions again.
 *
 * Two jobs. It tells the sorting queue which transactions came from an SMS rather than from
 * the user, and it carries the alert's dedupe key so the same message can never be imported
 * twice - re-delivered broadcasts and a backfill run over messages already captured live both
 * land on the same key.
 */
object SmsTransactionMarker {

    const val PREFIX = "Auto-imported from SMS"

    private const val PERSON_FLAG = "person"

    private val markerRegex = Regex("\\[sms:([^\\]:]+)(?::([^\\]]+))?]")

    /**
     * [paidToPerson] is carried along because the raw SMS is not stored, and without it the
     * sorting queue could not re-derive - or explain - the "looks like a fare" suggestion.
     */
    fun describe(refNo: String?, dedupeKey: String, paidToPerson: Boolean): String = buildString {
        append(PREFIX)
        if (refNo != null) append(" · Ref $refNo")
        append(" [sms:$dedupeKey")
        if (paidToPerson) append(":$PERSON_FLAG")
        append("]")
    }

    fun isAutoImported(description: String?): Boolean =
        description?.startsWith(PREFIX) == true

    fun dedupeKeyOf(description: String?): String? =
        description?.let { markerRegex.find(it)?.groupValues?.get(1) }

    fun paidToPerson(description: String?): Boolean =
        description?.let { markerRegex.find(it)?.groupValues?.get(2) } == PERSON_FLAG
}
