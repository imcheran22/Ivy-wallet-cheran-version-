package com.ivy.domain.quickadd

/**
 * Posts and removes the quick-add notification.
 *
 * An interface so Settings can switch the notification on and off the moment the toggle is
 * flipped, without the settings module depending on the `app` module where the notification
 * itself is built - the same arrangement [com.ivy.domain.sync.CloudSyncTrigger] uses.
 */
interface QuickAddNotifier {
    fun show()
    fun hide()
}
