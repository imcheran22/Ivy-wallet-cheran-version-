package com.ivy.domain.usecase.statement

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.ivy.base.threading.DispatchersProvider
import com.ivy.base.time.TimeConverter
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.Transaction
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.usecase.currency.CurrencyConverter
import com.ivy.domain.usecase.period.MonthPeriod
import com.ivy.domain.usecase.period.MonthPeriodProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.util.UUID
import javax.inject.Inject

/**
 * A month as a document someone else can read.
 *
 * CSV is for machines and spreadsheets; a statement is what you send to an accountant, a
 * landlord or a visa application. Drawn with the platform's own PdfDocument so it costs the app
 * no dependency and no network.
 */
class PdfStatementUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val currencyConverter: CurrencyConverter,
    private val periodProvider: MonthPeriodProvider,
    private val timeConverter: TimeConverter,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun exportCurrentMonth(uri: Uri): Result<Unit> =
        export(uri = uri, period = periodProvider.current())

    @Suppress("TooGenericExceptionCaught")
    suspend fun export(uri: Uri, period: MonthPeriod): Result<Unit> =
        withContext(dispatchersProvider.io) {
            runCatching {
                val document = buildDocument(period)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    document.writeTo(output)
                } ?: error("Could not open the file for writing")
                document.close()
            }
        }

    private suspend fun buildDocument(period: MonthPeriod): PdfDocument {
        val rates = currencyConverter.rates()
        val accounts = accountRepository.findAll().associate { it.id.value to it.name.value }
        val accountCurrencies = accountRepository.findAll()
            .associate { it.id.value to it.asset.code }
        val categories = categoryRepository.findAll().associate { it.id.value to it.name.value }

        val transactions = transactionRepository
            .findAllBetween(period.start, period.endExclusive)
            .filter { it.time >= period.start && it.time < period.endExclusive }
            .sortedBy { it.time }

        val rows = transactions.mapNotNull { transaction ->
            transaction.toRow(accounts, accountCurrencies, categories, rates)
        }

        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(pageInfo(pageNumber))
        var canvas = page.canvas
        var y = MARGIN + TITLE_SIZE

        canvas.drawText("Statement", MARGIN, y, titlePaint)
        y += LINE_HEIGHT
        canvas.drawText(period.label(), MARGIN, y, mutedPaint)
        y += LINE_HEIGHT * 2

        val income = rows.filter { it.isIncome }.sumOf { it.baseAmount }
        val expense = rows.filterNot { it.isIncome }.sumOf { it.baseAmount }

        canvas.drawText("Income: ${money(income)} ${rates.base}", MARGIN, y, bodyPaint)
        y += LINE_HEIGHT
        canvas.drawText("Expenses: ${money(expense)} ${rates.base}", MARGIN, y, bodyPaint)
        y += LINE_HEIGHT
        canvas.drawText("Net: ${money(income - expense)} ${rates.base}", MARGIN, y, boldPaint)
        y += LINE_HEIGHT * 2

        canvas.drawText("Date", MARGIN, y, boldPaint)
        canvas.drawText("Description", MARGIN + DATE_COLUMN, y, boldPaint)
        canvas.drawText("Amount", PAGE_WIDTH - MARGIN - AMOUNT_COLUMN, y, boldPaint)
        y += LINE_HEIGHT

        rows.forEach { row ->
            if (y > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(pageInfo(pageNumber))
                canvas = page.canvas
                y = MARGIN + LINE_HEIGHT
            }

            canvas.drawText(row.date, MARGIN, y, bodyPaint)
            canvas.drawText(row.description.take(DESCRIPTION_LIMIT), MARGIN + DATE_COLUMN, y, bodyPaint)
            canvas.drawText(
                "${if (row.isIncome) "+" else "-"}${money(row.baseAmount)}",
                PAGE_WIDTH - MARGIN - AMOUNT_COLUMN,
                y,
                bodyPaint,
            )
            y += LINE_HEIGHT
        }

        document.finishPage(page)
        return document
    }

    private fun Transaction.toRow(
        accounts: Map<UUID, String>,
        accountCurrencies: Map<UUID, String>,
        categories: Map<UUID, String>,
        rates: CurrencyConverter.Rates,
    ): StatementRow? {
        val (accountId, amount, isIncome) = when (this) {
            is Expense -> Triple(account.value, value.amount.value, false)
            is Income -> Triple(account.value, value.amount.value, true)
            else -> return null
        }

        val from = accountCurrencies[accountId] ?: rates.base
        val baseAmount = rates.convert(amount, from, rates.base) ?: amount
        val date = with(timeConverter) { time.toLocalDate() }

        return StatementRow(
            date = date.toString(),
            description = listOfNotNull(
                title?.value,
                category?.value?.let(categories::get),
                accounts[accountId],
            ).joinToString(" · "),
            baseAmount = baseAmount,
            isIncome = isIncome,
        )
    }

    private data class StatementRow(
        val date: String,
        val description: String,
        val baseAmount: Double,
        val isIncome: Boolean,
    )

    private fun pageInfo(pageNumber: Int) = PdfDocument.PageInfo
        .Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber)
        .create()

    private fun money(value: Double): String = AMOUNT_FORMAT.format(value)

    private val titlePaint = Paint().apply {
        textSize = TITLE_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val boldPaint = Paint().apply {
        textSize = BODY_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val bodyPaint = Paint().apply {
        textSize = BODY_SIZE
    }

    private val mutedPaint = Paint().apply {
        textSize = BODY_SIZE
        color = MUTED_COLOR
    }

    companion object {
        /** A4 at 72dpi, which is what PdfDocument's points map to. */
        private const val PAGE_WIDTH = 595f
        private const val PAGE_HEIGHT = 842f
        private const val MARGIN = 40f
        private const val LINE_HEIGHT = 18f
        private const val TITLE_SIZE = 22f
        private const val BODY_SIZE = 11f
        private const val DATE_COLUMN = 80f
        private const val AMOUNT_COLUMN = 90f
        private const val DESCRIPTION_LIMIT = 55
        private const val MUTED_COLOR = 0xFF666666.toInt()

        private val AMOUNT_FORMAT = DecimalFormat("#,##0.00")
    }
}
