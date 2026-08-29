package com.ivy.data.remote.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Row shapes for the Supabase (PostgREST) tables this app syncs to. Deliberately decoupled
 * from the local Room entities so local schema migrations don't leak into the cloud schema.
 * See `docs/supabase_schema.sql` for the matching table + RLS definitions.
 */
@Serializable
data class SupabaseAccountDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val currency: String? = null,
    val color: Int,
    val icon: String? = null,
    @SerialName("order_num") val orderNum: Double,
    @SerialName("include_in_balance") val includeInBalance: Boolean,
    @SerialName("bank_account_suffix") val bankAccountSuffix: String? = null,
    val archived: Boolean = false,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class SupabaseCategoryDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val color: Int,
    val icon: String? = null,
    @SerialName("order_num") val orderNum: Double,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class SupabaseTransactionDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("to_account_id") val toAccountId: String? = null,
    val type: String,
    val amount: Double,
    @SerialName("to_amount") val toAmount: Double? = null,
    val title: String? = null,
    val description: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("date_time") val dateTime: String? = null,
    @SerialName("attachment_url") val attachmentUrl: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)
