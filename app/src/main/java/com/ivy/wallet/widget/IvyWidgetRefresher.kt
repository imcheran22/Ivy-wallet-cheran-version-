package com.ivy.wallet.widget

import android.content.Context
import com.ivy.domain.WidgetRefresher
import com.ivy.widget.balance.WalletBalanceWidgetReceiver
import com.ivy.widget.budget.LeftToSpendWidgetReceiver
import com.ivy.widget.quickadd.QuickAddKeypadWidgetReceiver
import com.ivy.widget.quickadd.QuickAddPresetsWidgetReceiver
import com.ivy.widget.recent.RecentTransactionsWidgetReceiver
import com.ivy.widget.transaction.AddTransactionWidget
import com.ivy.widget.transaction.AddTransactionWidgetCompact
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class IvyWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefresher {

    override fun refreshAll() {
        AddTransactionWidget.updateBroadcast(context)
        AddTransactionWidgetCompact.updateBroadcast(context)
        WalletBalanceWidgetReceiver.updateBroadcast(context)
        QuickAddPresetsWidgetReceiver.updateBroadcast(context)
        QuickAddKeypadWidgetReceiver.updateBroadcast(context)
        LeftToSpendWidgetReceiver.updateBroadcast(context)
        RecentTransactionsWidgetReceiver.updateBroadcast(context)
    }
}
