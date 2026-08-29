package com.ivy.wallet

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.ivy.base.model.TransactionType
import com.ivy.wallet.quickadd.QuickAddActivity

/**
 * Logs an expense from the notification shade.
 *
 * Same rule as the widgets: the tile opens the quick-add sheet, not the app. Swiping down and
 * tapping should cost you the transaction, not your place in whatever you were doing.
 */
class PaymentTileService : TileService() {

    override fun onClick() {
        super.onClick()

        startQuickAdd()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startQuickAdd() {
        val intent = QuickAddActivity.intent(
            context = applicationContext,
            type = TransactionType.EXPENSE,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
