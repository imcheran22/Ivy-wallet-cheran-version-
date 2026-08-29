package com.ivy.domain.usecase.quickadd

import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * The accounts and categories a quick-add surface can offer, flattened to plain values.
 *
 * Widgets and notifications live in modules that can't see the data layer's types (and render
 * through RemoteViews, which has no idea what a `ColorInt` is), so everything they need to draw
 * a chip - name, colour, icon - is handed over as primitives.
 */
data class QuickAddAccountOption(
    val id: UUID,
    val name: String,
    val assetCode: String,
    val color: Int,
    val icon: String?,
)

data class QuickAddCategoryOption(
    val id: UUID,
    val name: String,
    val color: Int,
    val icon: String?,
)

data class QuickAddOptions(
    val accounts: List<QuickAddAccountOption>,
    val categories: List<QuickAddCategoryOption>,
) {
    val defaultAccount: QuickAddAccountOption?
        get() = accounts.firstOrNull()
}

class QuickAddOptionsUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val dispatchersProvider: DispatchersProvider,
) {
    suspend fun load(): QuickAddOptions = withContext(dispatchersProvider.io) {
        QuickAddOptions(
            accounts = accountRepository.findAll()
                .sortedBy { it.orderNum }
                .map {
                    QuickAddAccountOption(
                        id = it.id.value,
                        name = it.name.value,
                        assetCode = it.asset.code,
                        color = it.color.value,
                        icon = it.icon?.id,
                    )
                },
            categories = categoryRepository.findAll()
                .sortedBy { it.orderNum }
                .map {
                    QuickAddCategoryOption(
                        id = it.id.value,
                        name = it.name.value,
                        color = it.color.value,
                        icon = it.icon?.id,
                    )
                },
        )
    }
}
