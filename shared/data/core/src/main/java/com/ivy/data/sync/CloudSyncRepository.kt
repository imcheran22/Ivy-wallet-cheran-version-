package com.ivy.data.sync

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.ivy.base.model.TransactionType
import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.dao.write.WriteAccountDao
import com.ivy.data.db.dao.write.WriteCategoryDao
import com.ivy.data.db.dao.write.WriteTransactionDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.CategoryEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.remote.supabase.SupabaseAccountDto
import com.ivy.data.remote.supabase.SupabaseCategoryDto
import com.ivy.data.remote.supabase.SupabaseRestClient
import com.ivy.data.remote.supabase.SupabaseTransactionDto
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Pushes/pulls accounts, categories and transactions to/from Supabase as a full mirror
 * (upsert-by-id), scoped to this install via [CloudSyncSettings.ownerId]. There is no
 * multi-device conflict resolution beyond "last write wins per table push" - good enough for
 * a single-user backup/restore flow, not a substitute for real multi-device sync.
 */
class CloudSyncRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val writeAccountDao: WriteAccountDao,
    private val writeCategoryDao: WriteCategoryDao,
    private val writeTransactionDao: WriteTransactionDao,
    private val restClient: SupabaseRestClient,
    private val settings: CloudSyncSettings,
    private val dispatchersProvider: DispatchersProvider,
) {
    suspend fun pushAll(): Either<String, Unit> = withContext(dispatchersProvider.io) {
        either {
            val config = settings.supabaseConfigOrNull()
            ensureNotNull(config) { "Cloud sync isn't configured" }
            val ownerId = settings.ownerId()
            val nowIso = Instant.now().toString()

            restClient.upsert(
                config,
                "accounts",
                accountDao.findAll().map { it.toDto(ownerId, nowIso) }
            ).bind()

            restClient.upsert(
                config,
                "categories",
                categoryDao.findAll().map { it.toDto(ownerId, nowIso) }
            ).bind()

            restClient.upsert(
                config,
                "transactions",
                transactionDao.findAll().map { it.toDto(ownerId, nowIso) }
            ).bind()

            settings.setLastSyncedNow()
        }
    }

    /** Pulls the remote mirror down and upserts it locally - used for "restore on a new device". */
    suspend fun pullAll(): Either<String, Unit> = withContext(dispatchersProvider.io) {
        either {
            val config = settings.supabaseConfigOrNull()
            ensureNotNull(config) { "Cloud sync isn't configured" }
            val ownerId = settings.ownerId()

            val accounts = restClient
                .fetchAll<SupabaseAccountDto>(config, "accounts", ownerId)
                .bind()
            writeAccountDao.saveMany(accounts.mapNotNull { it.toEntityOrNull() })

            val categories = restClient
                .fetchAll<SupabaseCategoryDto>(config, "categories", ownerId)
                .bind()
            writeCategoryDao.saveMany(categories.mapNotNull { it.toEntityOrNull() })

            val transactions = restClient
                .fetchAll<SupabaseTransactionDto>(config, "transactions", ownerId)
                .bind()
            writeTransactionDao.saveMany(transactions.mapNotNull { it.toEntityOrNull() })

            settings.setLastSyncedNow()
        }
    }

    private fun AccountEntity.toDto(ownerId: String, updatedAt: String) = SupabaseAccountDto(
        id = id.toString(),
        ownerId = ownerId,
        name = name,
        currency = currency,
        color = color,
        icon = icon,
        orderNum = orderNum,
        includeInBalance = includeInBalance,
        bankAccountSuffix = bankAccountSuffix,
        updatedAt = updatedAt,
    )

    private fun CategoryEntity.toDto(ownerId: String, updatedAt: String) = SupabaseCategoryDto(
        id = id.toString(),
        ownerId = ownerId,
        name = name,
        color = color,
        icon = icon,
        orderNum = orderNum,
        updatedAt = updatedAt,
    )

    private fun TransactionEntity.toDto(ownerId: String, updatedAt: String) = SupabaseTransactionDto(
        id = id.toString(),
        ownerId = ownerId,
        accountId = accountId.toString(),
        toAccountId = toAccountId?.toString(),
        type = type.name,
        amount = amount,
        toAmount = toAmount,
        title = title,
        description = description,
        categoryId = categoryId?.toString(),
        dateTime = dateTime?.toString(),
        updatedAt = updatedAt,
    )

    private fun SupabaseAccountDto.toEntityOrNull(): AccountEntity? {
        val uuid = id.toUuidOrNull() ?: return null
        return AccountEntity(
            name = name,
            currency = currency,
            color = color,
            icon = icon,
            orderNum = orderNum,
            includeInBalance = includeInBalance,
            bankAccountSuffix = bankAccountSuffix,
            id = uuid,
        )
    }

    private fun SupabaseCategoryDto.toEntityOrNull(): CategoryEntity? {
        val uuid = id.toUuidOrNull() ?: return null
        return CategoryEntity(
            name = name,
            color = color,
            icon = icon,
            orderNum = orderNum,
            id = uuid,
        )
    }

    private fun SupabaseTransactionDto.toEntityOrNull(): TransactionEntity? {
        val uuid = id.toUuidOrNull() ?: return null
        val accId = accountId.toUuidOrNull() ?: return null
        val txType = runCatching { TransactionType.valueOf(type) }.getOrNull() ?: return null
        return TransactionEntity(
            accountId = accId,
            type = txType,
            amount = amount,
            toAccountId = toAccountId?.toUuidOrNull(),
            toAmount = toAmount,
            title = title,
            description = description,
            dateTime = dateTime?.let { runCatching { Instant.parse(it) }.getOrNull() },
            categoryId = categoryId?.toUuidOrNull(),
            id = uuid,
        )
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
