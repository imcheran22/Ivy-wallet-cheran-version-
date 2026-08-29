package com.ivy.ui

import android.content.Context
import com.ivy.domain.features.Features
import com.ivy.ui.time.DevicePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import javax.inject.Inject
import kotlin.math.abs

const val THOUSAND = 1_000
const val MILLION = 1_000_000
const val BILLION = 1_000_000_000
const val LAKH = 100_000
const val CRORE = 10_000_000
private const val INDIA_COUNTRY_CODE = "IN"

private fun usesIndianNumbering(): Boolean =
    java.util.Locale.getDefault().country == INDIA_COUNTRY_CODE

class FormatMoneyUseCase @Inject constructor(
    private val features: Features,
    private val devicePreferences: DevicePreferences,
    @ApplicationContext private val context: Context
) {

    private val locale = devicePreferences.locale()
    private val withoutDecimalFormatter = DecimalFormat("###,###", DecimalFormatSymbols(locale))
    private val withDecimalFormatter = DecimalFormat("###,###.00", DecimalFormatSymbols(locale))
    private val shortenAmountFormatter = DecimalFormat("###,###.##", DecimalFormatSymbols(locale))

    suspend fun format(value: Double, shortenAmount: Boolean): String {
        if (abs(value) >= THOUSAND && shortenAmount) {
            // Where money is spoken in lakhs and crores, "12.3m" is a number you have to
            // convert before you can read it.
            val result = if (usesIndianNumbering()) {
                when {
                    abs(value) >= CRORE -> "${shortenAmountFormatter.format(value / CRORE)}cr"
                    abs(value) >= LAKH -> "${shortenAmountFormatter.format(value / LAKH)}L"
                    else -> "${shortenAmountFormatter.format(value / THOUSAND)}k"
                }
            } else if (abs(value) >= BILLION) {
                "${shortenAmountFormatter.format(value / BILLION)}b"
            } else if (abs(value) >= MILLION) {
                "${shortenAmountFormatter.format(value / MILLION)}m"
            } else {
                "${shortenAmountFormatter.format(value / THOUSAND)}k"
            }
            return result
        } else {
            val showDecimalPoint = features.showDecimalNumber.isEnabled(context)

            val formatter = when (showDecimalPoint) {
                true -> withDecimalFormatter
                false -> withoutDecimalFormatter
            }
            return formatter.format(value)
        }
    }
}