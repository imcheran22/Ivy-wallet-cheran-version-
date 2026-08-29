package com.ivy.wallet.quickadd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.ivy.base.model.TransactionType
import com.ivy.base.time.TimeConverter
import com.ivy.base.time.TimeProvider
import com.ivy.design.api.IvyUI
import com.ivy.domain.AppStarter
import com.ivy.legacy.IvyWalletCtx
import com.ivy.legacy.appDesign
import com.ivy.ui.time.TimeFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.util.UUID
import javax.inject.Inject

/**
 * A transaction editor that never takes over the screen.
 *
 * Home-screen widgets render through RemoteViews, which has no text input of any kind, so an
 * amount typed by hand has to be typed somewhere else. This activity is that somewhere: it is
 * translucent, has no launcher entry and stays out of recents, so from the user's point of view
 * the widget expanded rather than the app opened.
 */
@AndroidEntryPoint
class QuickAddActivity : AppCompatActivity() {

    @Inject
    lateinit var ivyContext: IvyWalletCtx

    @Inject
    lateinit var timeConverter: TimeConverter

    @Inject
    lateinit var timeProvider: TimeProvider

    @Inject
    lateinit var timeFormatter: TimeFormatter

    @Inject
    lateinit var appStarter: AppStarter

    private val viewModel: QuickAddViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        viewModel.start(
            type = intent.readTransactionType(),
            presetId = intent.readPresetId(),
        )

        setContent {
            val state by viewModel.state.collectAsState()
            val secureScreen by viewModel.secureScreen.collectAsState()

            LaunchedEffect(secureScreen) {
                if (secureScreen) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE,
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(Unit) {
                viewModel.dismiss.collect { finish() }
            }

            // The confirmation is the undo window. Once it lapses there is nothing left to
            // decide, so the sheet gets out of the way on its own.
            LaunchedEffect(state.saved) {
                if (state.saved != null) {
                    delay(UNDO_WINDOW_MILLIS)
                    finish()
                }
            }

            IvyUI(
                design = appDesign(ivyContext),
                includeSurface = false,
                timeConverter = timeConverter,
                timeProvider = timeProvider,
                timeFormatter = timeFormatter,
            ) {
                QuickAddSheet(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onDismiss = ::finish,
                    onOpenFullEditor = {
                        appStarter.addTransactionStart(state.type)
                        finish()
                    },
                )
            }
        }
    }

    private fun Intent.readTransactionType(): TransactionType = runCatching {
        getStringExtra(EXTRA_TYPE)?.let(TransactionType::valueOf)
    }.getOrNull() ?: TransactionType.EXPENSE

    private fun Intent.readPresetId(): UUID? = runCatching {
        getStringExtra(EXTRA_PRESET_ID)?.let(UUID::fromString)
    }.getOrNull()

    companion object {
        const val EXTRA_TYPE = "quick_add_type"
        const val EXTRA_PRESET_ID = "quick_add_preset_id"

        private const val UNDO_WINDOW_MILLIS = 4_000L

        fun intent(
            context: Context,
            type: TransactionType,
            presetId: UUID? = null,
        ): Intent = Intent(context, QuickAddActivity::class.java).apply {
            putExtra(EXTRA_TYPE, type.name)
            presetId?.let { putExtra(EXTRA_PRESET_ID, it.toString()) }
        }
    }
}
