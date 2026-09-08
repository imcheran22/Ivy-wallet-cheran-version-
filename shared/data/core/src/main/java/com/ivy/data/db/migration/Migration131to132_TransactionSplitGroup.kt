package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * A payment can come out of more than one account at once - ₹100 of shopping paid
 * ₹70 in cash and ₹30 by UPI. A transaction row holds a single accountId, so each
 * part has to be its own row for the two balances to move correctly. This column is
 * what ties those rows back together as one expense.
 *
 * Null for every existing row, which is exactly right: they are all single-account.
 */
class Migration131to132_TransactionSplitGroup : Migration(131, 132) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN splitGroupId TEXT")
    }
}
