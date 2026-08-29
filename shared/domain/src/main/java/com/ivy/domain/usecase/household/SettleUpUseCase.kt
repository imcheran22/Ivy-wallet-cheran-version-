package com.ivy.domain.usecase.household

import com.ivy.data.model.LoanType
import com.ivy.base.threading.DispatchersProvider
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.LoanDao
import com.ivy.data.db.dao.write.WriteLoanDao
import com.ivy.data.db.entity.LoanEntity
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.abs

/**
 * Who owes whom at the end of a shared month.
 *
 * Two people paying for shared things out of separate accounts is the normal case, and the
 * arithmetic - halve the difference, not the total - is the part people get wrong at the
 * kitchen table.
 */
data class Settlement(
    val yourSpend: Double,
    val partnerSpend: Double,
    val currency: String,
) {
    /** Positive means they owe you; negative means you owe them. */
    val owedToYou: Double
        get() = (yourSpend - partnerSpend) / 2

    val amount: Double
        get() = abs(owedToYou)

    val settled: Boolean
        get() = amount < SETTLED_THRESHOLD

    companion object {
        /** Below this, splitting hairs costs more than the difference. */
        const val SETTLED_THRESHOLD = 0.01
    }
}

class SettleUpUseCase @Inject constructor(
    private val writeLoanDao: WriteLoanDao,
    private val loanDao: LoanDao,
    private val timeProvider: TimeProvider,
    private val dispatchersProvider: DispatchersProvider,
) {

    /**
     * Records the imbalance as a loan, so it stops being a thing you both half-remember and
     * starts being a number the app tracks until it's paid.
     */
    suspend fun record(
        partnerName: String,
        settlement: Settlement,
    ): Boolean = withContext(dispatchersProvider.io) {
        if (settlement.settled) return@withContext false

        writeLoanDao.save(
            LoanEntity(
                name = partnerName,
                amount = settlement.amount,
                type = if (settlement.owedToYou > 0) LoanType.LEND else LoanType.BORROW,
                dateTime = LocalDateTime.now(timeProvider.getZoneId()),
                note = SETTLEMENT_NOTE,
                orderNum = (loanDao.findMaxOrderNum() ?: 0.0) + 1,
            )
        )
        true
    }

    companion object {
        const val SETTLEMENT_NOTE = "Household settle-up"
    }
}
