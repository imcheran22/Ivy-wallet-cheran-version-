package com.ivy.domain.usecase.currency

import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.db.dao.read.ExchangeRatesDao
import com.ivy.data.db.dao.read.SettingsDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Converts money between the currencies the user actually holds.
 *
 * Rates are stored relative to the base currency, so a cross-rate is two hops. Everything that
 * adds up transactions from more than one account needs this, and all of them need it in a loop -
 * so rates are loaded once into a [Rates] snapshot and converted in memory, rather than hitting
 * the database per transaction.
 */
class CurrencyConverter @Inject constructor(
    private val exchangeRatesDao: ExchangeRatesDao,
    private val settingsDao: SettingsDao,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun baseCurrency(): String = withContext(dispatchersProvider.io) {
        settingsDao.findAll().firstOrNull()?.currency ?: DEFAULT_CURRENCY
    }

    suspend fun rates(): Rates = withContext(dispatchersProvider.io) {
        val base = baseCurrency()
        val rows = runCatching { exchangeRatesDao.findAll().first() }.getOrDefault(emptyList())
        Rates(
            base = base,
            ratesFromBase = rows
                .filter { it.baseCurrency.equals(base, ignoreCase = true) }
                .associate { it.currency.uppercase() to it.rate },
        )
    }

    data class Rates(
        val base: String,
        val ratesFromBase: Map<String, Double>,
    ) {
        /**
         * @return the converted amount, or null when there's no rate to get there - callers
         * decide whether to skip the value or show it unconverted rather than silently
         * pretending 1:1.
         */
        @Suppress("ReturnCount")
        fun convert(amount: Double, from: String, to: String): Double? {
            val fromCode = from.uppercase()
            val toCode = to.uppercase()
            val baseCode = base.uppercase()

            if (fromCode == toCode) return amount
            if (fromCode == baseCode) return ratesFromBase[toCode]?.let { amount * it }
            if (toCode == baseCode) {
                return ratesFromBase[fromCode]?.takeIf { it != 0.0 }?.let { amount / it }
            }

            val fromRate = ratesFromBase[fromCode]?.takeIf { it != 0.0 } ?: return null
            val toRate = ratesFromBase[toCode] ?: return null
            return amount / fromRate * toRate
        }

        fun toBase(amount: Double, from: String): Double? = convert(amount, from, base)
    }

    companion object {
        const val DEFAULT_CURRENCY = "USD"
    }
}
