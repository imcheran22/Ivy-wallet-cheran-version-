package com.ivy.notifmirror.sync

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class PartnerTransaction(
    val type: String,
    val amount: Double,
    val title: String,
    val currency: String,
    val category: String,
    val accountName: String,
    val dateTime: Long,
    val receivedAt: Long,
)

@Singleton
class PartnerTransactionStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("partner_transactions", Context.MODE_PRIVATE)

    fun save(tx: PartnerTransaction) {
        val list = loadAll().toMutableList()
        list.add(0, tx)
        if (list.size > MAX_STORED) {
            list.subList(MAX_STORED, list.size).clear()
        }
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_TRANSACTIONS, arr.toString()).apply()
    }

    fun loadAll(): List<PartnerTransaction> {
        val raw = prefs.getString(KEY_TRANSACTIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).toPartnerTransaction() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_TRANSACTIONS).apply()
    }

    private fun PartnerTransaction.toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("amount", amount)
        put("title", title)
        put("currency", currency)
        put("category", category)
        put("account_name", accountName)
        put("date_time", dateTime)
        put("received_at", receivedAt)
    }

    private fun JSONObject.toPartnerTransaction(): PartnerTransaction = PartnerTransaction(
        type = optString("type", "EXPENSE"),
        amount = optDouble("amount", 0.0),
        title = optString("title", ""),
        currency = optString("currency", ""),
        category = optString("category", ""),
        accountName = optString("account_name", ""),
        dateTime = optLong("date_time", 0L),
        receivedAt = optLong("received_at", 0L),
    )

    companion object {
        private const val KEY_TRANSACTIONS = "partner_txns"
        private const val MAX_STORED = 500
    }
}
