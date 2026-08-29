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

/** What a restore would replace, so the user can look before they leap. */
data class RestorePreview(
    val remoteAccounts: Int,
    val localAccounts: Int,
    val remoteCategories: Int,
    val localCategories: Int,
    val remoteTransactions: Int,
    val localTransactions: Int,
) {
    val remoteIsEmpty: Boolean
        get() = remoteAccounts == 0 && remoteCategories == 0 && remoteTransactions == 0
}

data class SyncResult(
    val pulled: Int,
    val pushed: Int,
)

/**
 * Two-way sync of accounts, categories and transactions with Supabase, scoped to
 * [CloudSyncSettings.ownerId] - which two devices can now share, turning this from a backup
 * into actual sync.
 *
 * [sync] merges per row rather than per table: anything the other device changed since this
 * one last synced is pulled down first, then local rows are pushed. Two devices editing
 * different rows both keep their edits, which is the case that used to silently lose data.
 * Two devices editing *the same* row since the last sync still resolve last-writer-wins - now
 * for that row alone instead of for the whole table.
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
    /**
     * What a restore would bring down, next to what is here now.
     *
     * "Restore from cloud" overwrites local rows, so it should never be a leap of faith: this
     * is the number the user checks before tapping it.
     */
    suspend fun previewRestore(): Either<String, RestorePreview> =
        withContext(dispatchersProvider.io) {
            either {
                val config = settings.supabaseConfigOrNull()
                ensureNotNull(config) { "Cloud sync isn't configured" }
                val ownerId = settings.ownerId()

                RestorePreview(
                    remoteAccounts = restClient
                        .fetchAll<SupabaseAccountDto>(config, "accounts", ownerId).bind().size,
                    localAccounts = accountDao.findAll().size,
                    remoteCategories = restClient
                        .fetchAll<SupabaseCategoryDto>(config, "categories", ownerId).bind().size,
                    localCategories = categoryDao.findAll().size,
                    remoteTransactions = restClient
                        .fetchAll<SupabaseTransactionDto>(config, "transactions", ownerId)
                        .bind().size,
                    localTransactions = transactionDao.findAll().size,
                )
            }
        }

    /**
     * Pull what changed elsewhere, then push what changed here.
     *
     * The cut-off is this device's last successful sync: a remote row stamped after it was
     * written by the other device in the meantime and wins over the copy here, which is stale
     * by definition. Rows older than that are left alone, so a device that has been offline for
     * a week can't drag the wallet backwards.
     */
    suspend fun sync(): Either<String, SyncResult> = withContext(dispatchersProvider.io) {
        either {
            val config = settings.supabaseConfigOrNull()
            ensureNotNull(config) { "Cloud sync isn't configured" }
            val ownerId = settings.ownerId()
            val since = settings.current().lastSyncedEpochMs?.let(Instant::ofEpochMilli)

            val accounts = restClient
                .fetchAll<SupabaseAccountDto>(config, "accounts", ownerId).bind()
                .filter { it.changedSince(since) }
            writeAccountDao.saveMany(accounts.mapNotNull { it.toEntityOrNull() })

            val categories = restClient
                .fetchAll<SupabaseCategoryDto>(config, "categories", ownerId).bind()
                .filter { it.changedSince(since) }
            writeCategoryDao.saveMany(categories.mapNotNull { it.toEntityOrNull() })

            val transactions = restClient
                .fetchAll<SupabaseTransactionDto>(config, "transactions", ownerId).bind()
                .filter { it.changedSince(since) }
            writeTransactionDao.saveMany(transactions.mapNotNull { it.toEntityOrNull() })

            val pulled = accounts.size + categories.size + transactions.size

            pushAll().bind()

            SyncResult(
                pulled = pulled,
                pushed = accountDao.findAll().size +
                    categoryDao.findAll().size +
                    transactionDao.findAll().size,
            )
        }
    }

    private fun String.parsedInstantOrNull(): Instant? =
        runCatching { Instant.parse(this) }.getOrNull()

    private fun SupabaseAccountDto.changedSince(since: Instant?): Boolean =
        since == null || (updatedAt.parsedInstantOrNull()?.isAfter(since) ?: true)

    private fun SupabaseCategoryDto.changedSince(since: Instant?): Boolean =
        since == null || (updatedAt.parsedInstantOrNull()?.isAfter(since) ?: true)

    private fun SupabaseTransactionDto.changedSince(since: Instant?): Boolean =
        since == null || (updatedAt.parsedInstantOrNull()?.isAfter(since) ?: true)

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
        archived = archived,
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
        attachmentUrl = attachmentUrl,
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
            archived = archived,
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
            attachmentUrl = attachmentUrl,
            id = uuid,
        )
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
