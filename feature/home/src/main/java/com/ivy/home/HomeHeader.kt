package com.ivy.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.base.model.TransactionType
import com.ivy.design.api.LocalTimeConverter
import com.ivy.design.api.LocalTimeFormatter
import com.ivy.design.api.LocalTimeProvider
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.design.utils.thenIf
import com.ivy.legacy.data.model.TimePeriod
import com.ivy.legacy.ivyWalletCtx
import com.ivy.legacy.ui.component.transaction.TransactionsDividerLine
import com.ivy.legacy.utils.clickableNoIndication
import com.ivy.legacy.utils.drawColoredShadow
import com.ivy.legacy.utils.format
import com.ivy.legacy.utils.horizontalSwipeListener
import com.ivy.legacy.utils.isNotNullOrBlank
import com.ivy.legacy.utils.rememberInteractionSource
import com.ivy.legacy.utils.rememberSwipeListenerState
import com.ivy.legacy.utils.springBounce
import com.ivy.legacy.utils.verticalSwipeListener
import com.ivy.navigation.PieChartStatisticScreen
import com.ivy.navigation.navigation
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gradient
import com.ivy.wallet.ui.theme.GradientGreen
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.BalanceRow
import com.ivy.wallet.ui.theme.components.BalanceRowMini
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import com.ivy.wallet.ui.theme.wallet.AmountCurrencyB1
import kotlin.math.absoluteValue

@ExperimentalAnimationApi
@Composable
internal fun HomeHeader(
    expanded: Boolean,
    name: String,
    period: TimePeriod,
    currency: String,
    balance: Double,
    onShowMonthModal: () -> Unit,
    onBalanceClick: () -> Unit,
    onSelectNextMonth: () -> Unit,
    hideBalance: Boolean,
    onHiddenBalanceClick: () -> Unit,
    onSelectPreviousMonth: () -> Unit,
) {
    Column {
        val percentExpanded by animateFloatAsState(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = springBounce(
                stiffness = Spring.StiffnessLow
            ),
            label = "Home Header Expand Collapse"
        )

        Spacer(Modifier.height(20.dp))

        HeaderStickyRow(
            percentExpanded = percentExpanded,
            name = name,
            period = period,
            currency = currency,
            balance = balance,
            hideBalance = hideBalance,

            onShowMonthModal = onShowMonthModal,
            onBalanceClick = onBalanceClick,
            onHiddenBalanceClick = onHiddenBalanceClick,
            onSelectNextMonth = onSelectNextMonth,
            onSelectPreviousMonth = onSelectPreviousMonth,
        )

        Spacer(Modifier.height(16.dp))

        if (percentExpanded < 0.5f) {
            TransactionsDividerLine(
                modifier = Modifier.alpha(1f - percentExpanded),
                paddingHorizontal = 0.dp
            )
        }
    }
}

@Composable
private fun HeaderStickyRow(
    percentExpanded: Float,
    name: String,
    period: TimePeriod,
    currency: String,
    balance: Double,
    onShowMonthModal: () -> Unit,
    onBalanceClick: () -> Unit,
    onSelectNextMonth: () -> Unit,
    hideBalance: Boolean,
    onHiddenBalanceClick: () -> Unit,
    onSelectPreviousMonth: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                modifier = Modifier
                    .alpha(percentExpanded)
                    .testTag("home_greeting_text"),
                text = if (name.isNotNullOrBlank()) {
                    stringResource(
                        R.string.hi_name,
                        name,
                    )
                } else {
                    stringResource(R.string.hi)
                },
                style = UI.typo.b1.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = UI.colors.pureInverse,
                ),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )

            // Balance mini row
            if (percentExpanded < 1f) {
                BalanceRowMini(
                    modifier = Modifier
                        .alpha(alpha = 1f - percentExpanded)
                        .clickableNoIndication(rememberInteractionSource()) {
                            if (hideBalance) {
                                onHiddenBalanceClick()
                            } else {
                                onBalanceClick()
                            }
                        },
                    currency = currency,
                    balance = balance,
                    shortenBigNumbers = true,
                    hiddenMode = hideBalance,
                    doubleRowDisplay = true,
                )
            }
        }

        IvyOutlinedButton(
            modifier = Modifier.horizontalSwipeListener(
                sensitivity = 75,
                state = rememberSwipeListenerState(),
                onSwipeLeft = {
                    onSelectNextMonth()
                },
                onSwipeRight = {
                    onSelectPreviousMonth()
                },
            ),
            iconStart = R.drawable.ic_calendar,
            text = period.toDisplayShort(
                startDateOfMonth = ivyWalletCtx().startDayOfMonth,
                timeConverter = LocalTimeConverter.current,
                timeProvider = LocalTimeProvider.current,
                timeFormatter = LocalTimeFormatter.current,
            ),
            minWidth = 130.dp,
        ) {
            onShowMonthModal()
        }

        Spacer(Modifier.width(12.dp))

        Spacer(Modifier.width(40.dp)) // settings menu button spacer
    }
}

@Suppress("LongParameterList")
@ExperimentalAnimationApi
@Composable
fun CashFlowInfo(
    currency: String,
    balance: Double,
    carryOver: Double,
    carryOverEnabled: Boolean,
    monthlyIncome: Double,
    monthlyExpenses: Double,
    incomeCount: Int,
    expenseCount: Int,
    unsortedIncomeCount: Int,
    unsortedExpenseCount: Int,
    spentToday: Double,
    projectedMonthEnd: Double,
    hideBalance: Boolean,
    hideIncome: Boolean,
    onHiddenIncomeClick: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    onBalanceClick: () -> Unit,
    percentExpanded: Float,
    onHiddenBalanceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalSwipeListener(
                sensitivity = Constants.SWIPE_DOWN_THRESHOLD_OPEN_MORE_MENU,
                state = rememberSwipeListenerState(),
                onSwipeDown = {
                    onOpenMoreMenu()
                },
            ),
    ) {
        BalanceRow(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .clickableNoIndication(rememberInteractionSource()) {
                    if (hideBalance) {
                        onHiddenBalanceClick()
                    } else {
                        onBalanceClick()
                    }
                }
                .testTag("home_balance"),
            currency = currency,
            balance = balance,
            shortenBigNumbers = true,
            hiddenMode = hideBalance
        )

        Spacer(modifier = Modifier.height(24.dp))

        IncomeExpenses(
            percentExpanded = percentExpanded,
            currency = currency,
            monthlyIncome = monthlyIncome,
            monthlyExpenses = monthlyExpenses,
            incomeCount = incomeCount,
            expenseCount = expenseCount,
            unsortedIncomeCount = unsortedIncomeCount,
            unsortedExpenseCount = unsortedExpenseCount,
            hideIncome = hideIncome,
            onHiddenIncomeClick = onHiddenIncomeClick
        )

        // The period total answers "how am I doing this month". It does not
        // answer "how much have I already spent today", which is the number a
        // daily tracker is consulted for and the only one you cannot get by
        // reading the list.
        if (spentToday > 0.0 && !hideBalance) {
            Spacer(Modifier.height(12.dp))

            Text(
                modifier = Modifier
                    .padding(start = 24.dp)
                    .testTag("home_spent_today"),
                text = stringResource(
                    R.string.spent_today,
                    spentToday.format(currency),
                    currency,
                ),
                style = UI.typo.nB2.style(color = Gray),
            )
        }

        // Spending pace. The period total says what has gone; this says where it
        // ends up if the rest of the month carries on like the part already spent,
        // which is the number that can still be acted on.
        if (projectedMonthEnd > 0.0 && !hideBalance) {
            Spacer(Modifier.height(4.dp))

            Text(
                modifier = Modifier
                    .padding(start = 24.dp)
                    .testTag("home_projected_month_end"),
                text = stringResource(
                    R.string.at_this_rate,
                    projectedMonthEnd.format(currency),
                    currency,
                ),
                style = UI.typo.nB2.style(color = Gray),
            )
        }

        // Cashflow, what the period opened with, and what that leaves are all one
        // story, so they are drawn together rather than as loose lines here.
        PeriodSummary(
            currency = currency,
            carryOver = carryOver,
            carryOverEnabled = carryOverEnabled,
            monthlyIncome = monthlyIncome,
            monthlyExpenses = monthlyExpenses,
            hideBalance = hideBalance,
            hideIncome = hideIncome,
        )
    }
}

/**
 * The bottom strip of the header: what the period started with, what it earned and spent, and
 * what that leaves.
 *
 * Income and expenses alone make every month look like it starts from nothing, which is why an
 * unspent August salary seemed to vanish on the 1st of September. [carryOver] is the balance the
 * period opened with, so `carried over + cashflow` is the money actually available to spend.
 */
@Composable
private fun PeriodSummary(
    currency: String,
    carryOver: Double,
    carryOverEnabled: Boolean,
    monthlyIncome: Double,
    monthlyExpenses: Double,
    hideBalance: Boolean,
    hideIncome: Boolean,
) {
    val cashflow = monthlyIncome - monthlyExpenses

    // Nothing carried in and nothing moved: an untouched month has no story to tell, so the
    // strip stays out of the way rather than printing a column of zeroes.
    val hasActivity = carryOver != 0.0 || cashflow != 0.0

    // Carried over is balance information; cashflow and what it adds up to would both let you
    // work the income back out, so they follow the income switch as well.
    val showCarriedOver = carryOverEnabled && hasActivity && !hideBalance
    val showCashflow = cashflow != 0.0 && !hideBalance && !hideIncome
    val showAvailable = carryOverEnabled && hasActivity && !hideBalance && !hideIncome

    if (!showCarriedOver && !showCashflow) {
        Spacer(Modifier.height(16.dp))
        return
    }

    Spacer(Modifier.height(12.dp))

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        if (showCarriedOver) {
            SummaryRow(
                label = stringResource(R.string.carried_over),
                amount = carryOver,
                currency = currency,
                color = UI.colors.pureInverse.copy(alpha = SUMMARY_LABEL_ALPHA),
            )
        }

        if (showCashflow) {
            Text(
                text = stringResource(
                    R.string.cashflow,
                    (if (cashflow > 0) "+" else ""),
                    cashflow.format(currency),
                    currency,
                ),
                style = UI.typo.nB2.style(
                    color = if (cashflow < 0) Gray else Green,
                ),
            )
        }

        if (showAvailable) {
            SummaryRow(
                label = stringResource(R.string.available_to_spend),
                amount = carryOver + cashflow,
                currency = currency,
                color = if (carryOver + cashflow < 0) Gray else UI.colors.pureInverse,
                emphasized = true,
            )
        }
    }

    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SummaryRow(
    label: String,
    amount: Double,
    currency: String,
    color: Color,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = UI.typo.nB2.style(
                color = color,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
            ),
        )

        Text(
            text = "${amount.format(currency)} $currency",
            style = UI.typo.nB2.style(
                color = color,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
            ),
        )
    }
}

private const val SUMMARY_LABEL_ALPHA = 0.6f

@Composable
private fun IncomeExpenses(
    percentExpanded: Float,
    currency: String,
    monthlyIncome: Double,
    monthlyExpenses: Double,
    incomeCount: Int,
    expenseCount: Int,
    unsortedIncomeCount: Int,
    unsortedExpenseCount: Int,
    hideIncome: Boolean,
    onHiddenIncomeClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(16.dp))

        val nav = navigation()

        HeaderCard(
            percentVisible = percentExpanded,
            icon = R.drawable.ic_income,
            backgroundGradient = GradientGreen,
            textColor = White,
            label = stringResource(R.string.income),
            currency = currency,
            amount = monthlyIncome,
            hiddenMode = hideIncome,
            testTag = "home_card_income",
            subtitle = countLabel(incomeCount, unsortedIncomeCount),
        ) {
            if (hideIncome) {
                onHiddenIncomeClick()
            } else {
                nav.navigateTo(
                    PieChartStatisticScreen(
                        type = TransactionType.INCOME,
                    ),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        HeaderCard(
            percentVisible = percentExpanded,
            icon = R.drawable.ic_expense,
            backgroundGradient = Gradient(UI.colors.pureInverse, UI.colors.gray),
            textColor = UI.colors.pure,
            label = stringResource(R.string.expenses),
            currency = currency,
            amount = monthlyExpenses.absoluteValue,
            testTag = "home_card_expense",
            subtitle = countLabel(expenseCount, unsortedExpenseCount),
        ) {
            nav.navigateTo(
                PieChartStatisticScreen(
                    type = TransactionType.EXPENSE,
                ),
            )
        }

        Spacer(Modifier.width(16.dp))
    }
}

@Composable
private fun RowScope.HeaderCard(
    @DrawableRes icon: Int,
    backgroundGradient: Gradient,
    percentVisible: Float,
    textColor: Color,
    label: String,
    currency: String,
    amount: Double,
    testTag: String,
    subtitle: String,
    hiddenMode: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .thenIf(percentVisible == 1f) {
                drawColoredShadow(backgroundGradient.startColor)
            }
            .clip(UI.shapes.r4)
            .background(backgroundGradient.asHorizontalBrush())
            .testTag(testTag)
            .clickable(
                onClick = onClick,
            ),
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(16.dp))

            IvyIcon(
                icon = icon,
                tint = textColor,
            )

            Spacer(Modifier.width(4.dp))

            Text(
                text = label,
                style = UI.typo.c.style(
                    color = textColor,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(20.dp))

            if (hiddenMode) {
                // Masked the same way as the balance row, so "hidden" looks the same
                // everywhere on the screen.
                Text(
                    text = "****",
                    style = UI.typo.nB1.style(
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            } else {
                AmountCurrencyB1(
                    amount = amount,
                    currency = currency,
                    textColor = textColor,
                    shortenBigNumbers = true,
                )
            }

            Spacer(Modifier.width(4.dp))
        }

        // Says what the number is made of. A total with no provenance is a number you have to
        // take on trust, and the whole point of auto-capture is that you shouldn't have to.
        Spacer(Modifier.height(2.dp))

        Text(
            modifier = Modifier.padding(horizontal = 20.dp),
            text = subtitle,
            style = UI.typo.nC.style(
                color = textColor.copy(alpha = SUBTITLE_ALPHA),
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))
    }
}

private const val SUBTITLE_ALPHA = 0.75f

/**
 * Counts are deliberately plain: "6 payments" beats "6" because the card is read at a glance
 * and the unit is the thing that makes it parse.
 */
private fun countLabel(count: Int, unsorted: Int): String = when {
    count == 0 -> "Nothing yet"
    unsorted > 0 -> "$count · $unsorted to sort"
    else -> "$count this month"
}
