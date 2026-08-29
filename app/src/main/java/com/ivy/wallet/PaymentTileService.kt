package com.ivy.wallet

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.ivy.base.model.TransactionType

private const val ACTION_ADD_TRANSACTION = "ivy.wallet.intent.action.add_transaction"

class PaymentTileService : TileService() {
    // Called when the user adds your tile.
    override fun onTileAdded() {
        super.onTileAdded()
    }

    // Called when your app can update your tile.
    override fun onStartListening() {
        super.onStartListening()
    }

    // Called when your app can no longer update your tile.
    override fun onStopListening() {
        super.onStopListening()
    }

    // Called when the user taps on your tile in an active or inactive state.
    override fun onClick() {
        super.onClick()

        startRootActivity()
    }

    // Called when the user removes your tile.
    override fun onTileRemoved() {
        super.onTileRemoved()
    }

    /**
     * The tile is pulled down mid-payment, so it opens the amount keypad with expense already
     * chosen rather than the home screen. Same action and extra the launcher shortcuts use -
     * one entry point, one code path in [RootViewModel.handleIntent].
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startRootActivity() {
        val intent = Intent(applicationContext, RootActivity::class.java).apply {
            action = ACTION_ADD_TRANSACTION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(
                RootViewModel.EXTRA_ADD_TRANSACTION_TYPE,
                TransactionType.EXPENSE.name,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
