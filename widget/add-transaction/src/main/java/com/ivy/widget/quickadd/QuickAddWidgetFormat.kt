package com.ivy.widget.quickadd

import java.text.DecimalFormat

private val amountFormat = DecimalFormat("###,###.##")

/**
 * Widget-side money formatting.
 *
 * Widgets render outside the app's Compose tree, so they can't reach the app's formatting
 * settings; this keeps it to grouped digits and at most two decimals, which reads correctly in
 * every locale the app ships in.
 */
internal fun formatAmount(value: Double): String = amountFormat.format(value)
