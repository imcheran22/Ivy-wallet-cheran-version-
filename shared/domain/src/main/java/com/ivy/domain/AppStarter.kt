package com.ivy.domain

import android.content.Intent
import com.ivy.base.model.TransactionType
import java.util.UUID

/**
 * A component used to start the **RootActivity** without knowing about it.
 */
interface AppStarter {
    fun getRootIntent(): Intent
    fun defaultStart()
    fun addTransactionStart(type: TransactionType)

    /**
     * Opens the floating quick-add sheet over whatever the user is currently looking at,
     * instead of taking them into the app.
     *
     * [presetId] files that preset straight away and shows the undo confirmation - it's what a
     * one-tap preset button on a widget or a notification action ends up calling.
     */
    fun quickAddStart(type: TransactionType, presetId: UUID? = null)

    /**
     * The same sheet as an [Intent], for callers that must hand a `PendingIntent` to the system
     * (widgets, notifications) instead of starting the activity themselves - a background
     * activity start would be dropped on modern Android.
     */
    fun getQuickAddIntent(type: TransactionType, presetId: UUID? = null): Intent

    /** Opens an existing transaction in the full editor - what a widget row taps through to. */
    fun getEditTransactionIntent(transactionId: UUID, type: TransactionType): Intent
}
