package com.ivy.wallet

import android.content.Context
import android.content.Intent
import com.ivy.base.model.TransactionType
import com.ivy.domain.AppStarter
import com.ivy.wallet.quickadd.QuickAddActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

class IvyAppStarter @Inject constructor(
    @ApplicationContext
    private val context: Context
) : AppStarter {

    override fun getRootIntent(): Intent {
        return Intent(context, RootActivity::class.java)
    }

    override fun defaultStart() {
        context.startActivity(
            getRootIntent().apply {
                applyWidgetStartFlags()
            }
        )
    }

    override fun addTransactionStart(type: TransactionType) {
        context.startActivity(
            getRootIntent().apply {
                putExtra(RootViewModel.EXTRA_ADD_TRANSACTION_TYPE, type)
                applyWidgetStartFlags()
            }
        )
    }

    override fun quickAddStart(type: TransactionType, presetId: UUID?) {
        context.startActivity(getQuickAddIntent(type = type, presetId = presetId))
    }

    override fun getQuickAddIntent(type: TransactionType, presetId: UUID?): Intent =
        QuickAddActivity.intent(
            context = context,
            type = type,
            presetId = presetId,
        ).apply {
            // No CLEAR_TASK here: the sheet floats over the launcher in its own task and must
            // not drag the main app task to the front behind it.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    override fun getEditTransactionIntent(transactionId: UUID, type: TransactionType): Intent =
        getRootIntent().apply {
            putExtra(RootViewModel.EXTRA_EDIT_TRANSACTION_ID, transactionId.toString())
            putExtra(RootViewModel.EXTRA_ADD_TRANSACTION_TYPE, type.name)
            applyWidgetStartFlags()
        }

    override fun getSortingQueueIntent(): Intent = getRootIntent().apply {
        putExtra(RootViewModel.EXTRA_OPEN_SCREEN, RootViewModel.SCREEN_SORTING_QUEUE)
        applyWidgetStartFlags()
    }

    private fun Intent.applyWidgetStartFlags() {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
}
