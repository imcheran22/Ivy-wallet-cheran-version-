package com.ivy.domain.usecase.sms

import com.ivy.base.threading.DispatchersProvider
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Re-reads the SMS inbox and imports anything the live broadcast missed.
 *
 * A `SMS_RECEIVED` receiver is a best-effort signal, not a guarantee. Android delivers it only
 * while the app holds the runtime permission, and several OEM builds put apps into a state
 * where background receivers simply stop being called - the app looks enabled, the switch is
 * on, and nothing arrives. That failure is invisible from inside the app, which makes it the
 * worst kind: the numbers are wrong and nothing says so.
 *
 * So the receiver is treated as the fast path, not the source of truth. Every time the app is
 * opened this sweep walks the inbox and offers each money-shaped message to the importer,
 * which rejects the ones already captured on their dedupe key. Slower, but it cannot silently
 * lose a transaction, and being right late beats being wrong forever.
 */
class SmsCatchUpUseCase @Inject constructor(
    private val reader: DeviceSmsReader,
    private val importSmsTransactionUseCase: ImportSmsTransactionUseCase,
    private val captureLog: SmsCaptureLog,
    private val dispatchersProvider: DispatchersProvider,
) {

    data class Outcome(
        val scanned: Int,
        val imported: Int,
        val alreadyKnown: Int,
        val unparsed: Int,
        val blockedReason: String?,
    ) {
        val ran: Boolean get() = blockedReason == null
    }

    /**
     * [force] is what the "Catch up now" button passes: it ignores the watermark and re-reads
     * the full window, so a user who suspects something was missed can prove it either way
     * rather than being told to trust the same mechanism that just failed them.
     */
    suspend fun sweep(force: Boolean = false): Outcome = withContext(dispatchersProvider.io) {
        if (!captureLog.isAutoCaptureEnabled()) {
            return@withContext blocked("Auto-import is off")
        }
        if (!reader.hasPermission()) {
            return@withContext blocked("SMS permission not granted")
        }

        val state = captureLog.read()
        // `force` ignores the watermark, never the start date: re-reading is for messages the
        // receiver may have missed, not for backfilling history the user chose to leave out.
        val watermarkFloor = if (force) null else state.watermark?.minus(OVERLAP)
        val messages = reader.readRecent()
            .filter { watermarkFloor == null || it.receivedAt.isAfter(watermarkFloor) }
            .filter { state.importFrom == null || !it.receivedAt.isBefore(state.importFrom) }
            .filter { BankSmsParser.looksLikeMoneyAlert(it.body) }

        var imported = 0
        var alreadyKnown = 0
        var unparsed = 0
        // Oldest first, so the running balance a bank prints stays in chronological order.
        messages.sortedBy { it.receivedAt }.forEach { sms ->
            when (importSmsTransactionUseCase.import(sms.body, sms.receivedAt)) {
                is ImportSmsTransactionUseCase.Result.Imported -> imported++
                ImportSmsTransactionUseCase.Result.AlreadyImported -> alreadyKnown++
                // Out of scope by the user's own choice; counting it as "not understood"
                // would report a parser failure that never happened.
                ImportSmsTransactionUseCase.Result.BeforeImportWindow -> alreadyKnown++
                else -> unparsed++
            }
        }

        captureLog.recordSweep(
            imported = imported,
            summary = summarise(messages.size, imported, alreadyKnown, unparsed),
            newestSeen = messages.maxOfOrNull { it.receivedAt },
        )

        Outcome(
            scanned = messages.size,
            imported = imported,
            alreadyKnown = alreadyKnown,
            unparsed = unparsed,
            blockedReason = null,
        )
    }

    private fun summarise(scanned: Int, imported: Int, known: Int, unparsed: Int): String = when {
        scanned == 0 -> "No new bank messages"
        imported == 0 && unparsed == 0 -> "$known already captured"
        unparsed == 0 -> "Captured $imported of $scanned"
        else -> "Captured $imported of $scanned, $unparsed not understood"
    }

    private fun blocked(reason: String) = Outcome(
        scanned = 0,
        imported = 0,
        alreadyKnown = 0,
        unparsed = 0,
        blockedReason = reason,
    )

    private companion object {
        /**
         * Sweeps re-read a little before the watermark. A message's inbox timestamp is set by
         * the sender's network, so one can land fractionally behind a message already seen;
         * starting exactly at the watermark would step over it. Re-offering is free - the
         * importer dedupes - whereas skipping is permanent.
         */
        val OVERLAP: Duration = Duration.ofHours(6)
    }
}
