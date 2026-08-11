package com.ivy.domain.usecase.sms

/**
 * The handful of categories auto-capture depends on, over and above whatever the user has
 * already created. They exist because the machine can log the money but cannot know what it
 * was for, and the honest answers to that question don't fit the usual category list.
 */
object SmsCategories {

    /**
     * Replaces the usual "Other". "Other" tells you nothing; ₹4,000 you genuinely cannot
     * account for tells you something real, so the queue says so out loud.
     */
    const val I_DONT_REMEMBER = "I don't remember"

    /** Most autos and cabs go to a different driver's personal UPI every single time. */
    const val CABS_AND_AUTOS = "Cabs & autos"

    /** Income-side category: a transfer between the user's own accounts is not earnings. */
    const val MOVED_BETWEEN_ACCOUNTS = "Moved between my accounts"

    /** Income-side category: a refund is money coming back, not money earned. */
    const val REFUND = "Refund"

    /**
     * Categories that must be excluded from any "what did I earn" total. Without this, a
     * transfer between two of your own accounts inflates your earnings into fiction.
     */
    val excludedFromEarnings: Set<String> = setOf(MOVED_BETWEEN_ACCOUNTS, REFUND)

    /**
     * Created on demand by the sorting queue if the user doesn't already have them.
     */
    val autoCaptureDefaults: List<String> = listOf(
        I_DONT_REMEMBER,
        CABS_AND_AUTOS,
        MOVED_BETWEEN_ACCOUNTS,
        REFUND,
    )
}
