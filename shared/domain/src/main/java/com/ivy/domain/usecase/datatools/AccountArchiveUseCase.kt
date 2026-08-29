package com.ivy.domain.usecase.datatools

import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.model.AccountId
import com.ivy.data.repository.AccountRepository
import com.ivy.domain.WidgetRefresher
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class ArchivableAccount(
    val id: UUID,
    val name: String,
    val assetCode: String,
    val archived: Boolean,
)

/**
 * Archiving, as the honest alternative to deleting.
 *
 * A closed bank account still explains where money went last year. Deleting it takes that
 * history with it; archiving only takes it out of the pickers.
 */
class AccountArchiveUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val widgetRefresher: WidgetRefresher,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun accounts(): List<ArchivableAccount> = withContext(dispatchersProvider.io) {
        accountRepository.findAll()
            .sortedWith(compareBy({ it.archived }, { it.orderNum }))
            .map {
                ArchivableAccount(
                    id = it.id.value,
                    name = it.name.value,
                    assetCode = it.asset.code,
                    archived = it.archived,
                )
            }
    }

    suspend fun setArchived(accountId: UUID, archived: Boolean) =
        withContext(dispatchersProvider.io) {
            val account = accountRepository.findById(AccountId(accountId)) ?: return@withContext
            accountRepository.save(account.copy(archived = archived))
            widgetRefresher.refreshAll()
        }
}
