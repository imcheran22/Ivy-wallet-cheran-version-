package com.ivy.base

interface CoupleTransactionSyncer {
    fun syncTransaction(
        type: String,
        amount: Double,
        title: String,
        currency: String,
        category: String,
        accountName: String,
        dateTimeMillis: Long,
    )
}
