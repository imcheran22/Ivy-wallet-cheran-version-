package com.ivy.domain

/**
 * Tells the home-screen widgets that the ledger changed.
 *
 * Widgets render from a snapshot taken when they were last updated, so anything written outside
 * the app - a preset tap, a notification action - has to say so explicitly or the balance on the
 * home screen quietly drifts away from the truth.
 */
interface WidgetRefresher {
    fun refreshAll()
}
