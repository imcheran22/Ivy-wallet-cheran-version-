package com.ivy.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.legacy.Theme
import com.ivy.base.legacy.refreshWidget
import com.ivy.data.backup.BackupDataUseCase
import com.ivy.data.datastore.DatastoreKeys
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.write.WriteSettingsDao
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.sync.CloudSyncRepository
import com.ivy.data.sync.CloudSyncSettings
import com.ivy.domain.RootScreen
import com.ivy.domain.sync.CloudSyncTrigger
import com.ivy.domain.usecase.csv.ExportCsvUseCase
import com.ivy.domain.usecase.exchange.SyncExchangeRatesUseCase
import com.ivy.domain.usecase.sms.DeviceSmsReader
import com.ivy.domain.usecase.sms.SmsCaptureLog
import com.ivy.domain.usecase.sms.SmsCatchUpUseCase
import com.ivy.frp.monad.Res
import com.ivy.legacy.IvyWalletCtx
import com.ivy.legacy.LogoutLogic
import com.ivy.legacy.domain.action.settings.UpdateSettingsAct
import com.ivy.legacy.utils.getISOFormattedDateTime
import com.ivy.legacy.utils.ioThread
import com.ivy.legacy.utils.timeNowUTC
import com.ivy.legacy.utils.uiThread
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.action.global.StartDayOfMonthAct
import com.ivy.wallet.domain.action.global.UpdateStartDayOfMonthAct
import com.ivy.wallet.domain.action.settings.SettingsAct
import com.ivy.widget.balance.WalletBalanceWidgetReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
@SuppressLint("StaticFieldLeak")
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val ivyContext: IvyWalletCtx,
    private val logoutLogic: LogoutLogic,
    private val sharedPrefs: SharedPrefs,
    private val backupDataUseCase: BackupDataUseCase,
    private val startDayOfMonthAct: StartDayOfMonthAct,
    private val updateStartDayOfMonthAct: UpdateStartDayOfMonthAct,
    private val syncExchangeRatesUseCase: SyncExchangeRatesUseCase,
    private val settingsAct: SettingsAct,
    private val updateSettingsAct: UpdateSettingsAct,
    private val settingsWriter: WriteSettingsDao,
    private val exportCsvUseCase: ExportCsvUseCase,
    private val dataStore: DataStore<Preferences>,
    private val cloudSyncSettings: CloudSyncSettings,
    private val cloudSyncRepository: CloudSyncRepository,
    private val cloudSyncTrigger: CloudSyncTrigger,
    private val smsCaptureLog: SmsCaptureLog,
    private val smsCatchUpUseCase: SmsCatchUpUseCase,
    private val deviceSmsReader: DeviceSmsReader,
    @ApplicationContext private val context: Context
) : ComposeViewModel<SettingsState, SettingsEvent>() {

    private val currencyCode = mutableStateOf("")
    private val name = mutableStateOf("")
    private val currentTheme = mutableStateOf<Theme>(Theme.AUTO)
    private val lockApp = mutableStateOf(false)
    private val showNotifications = mutableStateOf(true)
    private val hideCurrentBalance = mutableStateOf(false)
    private val hideIncome = mutableStateOf(false)
    private val treatTransfersAsIncomeExpense = mutableStateOf(false)
    private val startDateOfMonth = mutableIntStateOf(1)
    private val progressState = mutableStateOf(false)
    private val smsAutoImportEnabled = mutableStateOf(false)
    private val cloudSyncEnabled = mutableStateOf(false)
    private val cloudSyncSupabaseUrl = mutableStateOf("")
    private val cloudSyncSupabaseAnonKey = mutableStateOf("")
    private val cloudSyncInProgress = mutableStateOf(false)
    private val cloudSyncLastSyncedEpochMs = mutableLongStateOf(0L)
    private val cloudSyncError = mutableStateOf<String?>(null)
    private val smsCapture = mutableStateOf(SmsCaptureSummary())

    @Composable
    override fun uiState(): SettingsState {
        LaunchedEffect(Unit) {
            onStart()
        }

        return SettingsState(
            currencyCode = getCurrencyCode(),
            name = getName(),
            currentTheme = getCurrentTheme(),
            lockApp = getLockApp(),
            showNotifications = getShowNotifications(),
            hideCurrentBalance = getHideCurrentBalance(),
            treatTransfersAsIncomeExpense = getTreatTransfersAsIncomeExpense(),
            startDateOfMonth = getStartDateOfMonth(),
            progressState = getProgressState(),
            hideIncome = getHideIncome(),
            languageOptionVisible = isLanguageOptionVisible(),
            smsAutoImportEnabled = smsAutoImportEnabled.value,
            cloudSyncEnabled = cloudSyncEnabled.value,
            cloudSyncSupabaseUrl = cloudSyncSupabaseUrl.value,
            cloudSyncSupabaseAnonKey = cloudSyncSupabaseAnonKey.value,
            cloudSyncInProgress = cloudSyncInProgress.value,
            cloudSyncLastSyncedEpochMs = cloudSyncLastSyncedEpochMs.longValue.takeIf { it > 0 },
            cloudSyncError = cloudSyncError.value,
            smsCapture = smsCapture.value,
        )
    }

    private suspend fun onStart() {
        initializeCurrency()
        initializeName()
        initializeCurrentTheme()
        initializeLockApp()
        initializeShowNotifications()
        initializeHideCurrentBalance()
        initializeHideIncome()
        initializeTransfersAsIncomeExpense()
        initializeStartDateOfMonth()
        initializeSmsAutoImport()
        initializeCloudSync()
    }

    private suspend fun initializeSmsAutoImport() {
        smsAutoImportEnabled.value = dataStore.data.first()[DatastoreKeys.SMS_AUTO_IMPORT_ENABLED] ?: false
        refreshSmsCaptureSummary()
    }

    private suspend fun refreshSmsCaptureSummary(sweeping: Boolean = false) {
        val log = smsCaptureLog.read()
        smsCapture.value = SmsCaptureSummary(
            enabled = smsAutoImportEnabled.value,
            permissionGranted = deviceSmsReader.hasPermission(),
            capturedTotal = log.capturedTotal,
            lastCaptureAtEpochMs = log.lastCaptureAt?.toEpochMilli(),
            lastSweepAtEpochMs = log.lastSweepAt?.toEpochMilli(),
            lastSweepSummary = log.lastSweepSummary,
            sweeping = sweeping,
        )
    }

    private fun catchUpOnSms() {
        viewModelScope.launch {
            refreshSmsCaptureSummary(sweeping = true)
            val outcome = smsCatchUpUseCase.sweep(force = true)
            refreshSmsCaptureSummary()
            outcome.blockedReason?.let { reason ->
                smsCapture.value = smsCapture.value.copy(lastSweepSummary = reason)
            }
        }
    }

    private suspend fun initializeCloudSync() {
        val prefs = cloudSyncSettings.current()
        cloudSyncEnabled.value = prefs.enabled
        cloudSyncSupabaseUrl.value = prefs.supabaseUrl
        cloudSyncSupabaseAnonKey.value = prefs.supabaseAnonKey
        cloudSyncLastSyncedEpochMs.longValue = prefs.lastSyncedEpochMs ?: 0L
    }

    private suspend fun initializeCurrency() {
        val settings = ioThread {
            settingsDao.findFirst()
        }

        currencyCode.value = settings.currency
    }

    private suspend fun initializeName() {
        val settings = ioThread {
            settingsDao.findFirst()
        }

        name.value = settings.name
    }

    private suspend fun initializeCurrentTheme() {
        currentTheme.value = settingsAct(Unit).theme
    }

    private fun initializeLockApp() {
        lockApp.value = sharedPrefs.getBoolean(SharedPrefs.APP_LOCK_ENABLED, false)
    }

    private fun initializeShowNotifications() {
        showNotifications.value = sharedPrefs.getBoolean(
            SharedPrefs.SHOW_NOTIFICATIONS, true
        )
    }

    private fun initializeHideCurrentBalance() {
        hideCurrentBalance.value =
            sharedPrefs.getBoolean(SharedPrefs.HIDE_CURRENT_BALANCE, false)
    }

    private fun initializeHideIncome() {
        hideIncome.value =
            sharedPrefs.getBoolean(SharedPrefs.HIDE_INCOME, false)
    }

    private fun initializeTransfersAsIncomeExpense() {
        treatTransfersAsIncomeExpense.value =
            sharedPrefs.getBoolean(SharedPrefs.TRANSFERS_AS_INCOME_EXPENSE, false)
    }

    private suspend fun initializeStartDateOfMonth() {
        startDateOfMonth.intValue = startDayOfMonthAct(Unit)
    }

    @Composable
    private fun getCurrencyCode(): String {
        return currencyCode.value
    }

    @Composable
    private fun getName(): String {
        return name.value
    }

    @Composable
    private fun getCurrentTheme(): Theme {
        return currentTheme.value
    }

    @Composable
    private fun getLockApp(): Boolean {
        return lockApp.value
    }

    @Composable
    private fun getShowNotifications(): Boolean {
        return showNotifications.value
    }

    @Composable
    private fun getHideCurrentBalance(): Boolean {
        return hideCurrentBalance.value
    }

    @Composable
    private fun getHideIncome(): Boolean {
        return hideIncome.value
    }

    @Composable
    private fun getTreatTransfersAsIncomeExpense(): Boolean {
        return treatTransfersAsIncomeExpense.value
    }

    @Composable
    private fun getStartDateOfMonth(): String {
        return startDateOfMonth.intValue.toString()
    }

    @Composable
    private fun getProgressState(): Boolean {
        return progressState.value
    }

    private fun isLanguageOptionVisible(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SetCurrency -> setCurrency(event.newCurrency)
            is SettingsEvent.SetName -> setName(event.newName)
            is SettingsEvent.ExportToCsv -> exportToCSV(event.rootScreen)
            is SettingsEvent.BackupData -> exportToZip(event.rootScreen)
            SettingsEvent.SwitchTheme -> switchTheme()
            is SettingsEvent.SetLockApp -> setLockApp(event.lockApp)
            is SettingsEvent.SetShowNotifications -> setShowNotifications(event.showNotifications)
            is SettingsEvent.SetHideCurrentBalance -> setHideCurrentBalance(
                event.hideCurrentBalance
            )

            is SettingsEvent.SetHideIncome -> setHideIncome(
                event.hideIncome
            )

            is SettingsEvent.SetTransfersAsIncomeExpense -> setTransfersAsIncomeExpense(
                event.treatTransfersAsIncomeExpense
            )

            is SettingsEvent.SetStartDateOfMonth -> setStartDateOfMonth(event.startDate)

            SettingsEvent.DeleteCloudUserData -> deleteCloudUserData()
            SettingsEvent.DeleteAllUserData -> deleteAllUserData()
            SettingsEvent.SwitchLanguage -> switchLanguage()

            is SettingsEvent.SetSmsAutoImportEnabled -> setSmsAutoImportEnabled(event.enabled)
            SettingsEvent.CatchUpOnSms -> catchUpOnSms()
            is SettingsEvent.SetCloudSyncEnabled -> setCloudSyncEnabled(event.enabled)
            is SettingsEvent.SetCloudSyncCredentials -> setCloudSyncCredentials(
                event.url,
                event.anonKey
            )

            SettingsEvent.TriggerCloudSyncNow -> triggerCloudSyncNow()
            SettingsEvent.TriggerCloudRestore -> triggerCloudRestore()
        }
    }

    private fun setSmsAutoImportEnabled(enabled: Boolean) {
        smsAutoImportEnabled.value = enabled

        viewModelScope.launch {
            dataStore.edit { it[DatastoreKeys.SMS_AUTO_IMPORT_ENABLED] = enabled }
        }
    }

    private fun setCloudSyncEnabled(enabled: Boolean) {
        cloudSyncEnabled.value = enabled

        viewModelScope.launch {
            cloudSyncSettings.setEnabled(enabled)
            if (enabled) {
                cloudSyncTrigger.schedulePeriodic()
            } else {
                cloudSyncTrigger.cancelPeriodic()
            }
        }
    }

    private fun setCloudSyncCredentials(url: String, anonKey: String) {
        cloudSyncSupabaseUrl.value = url
        cloudSyncSupabaseAnonKey.value = anonKey

        viewModelScope.launch {
            cloudSyncSettings.setCredentials(url, anonKey)
        }
    }

    private fun triggerCloudSyncNow() {
        viewModelScope.launch {
            cloudSyncInProgress.value = true
            cloudSyncError.value = null
            cloudSyncRepository.pushAll().fold(
                ifLeft = { error -> cloudSyncError.value = error },
                ifRight = {
                    cloudSyncLastSyncedEpochMs.longValue = System.currentTimeMillis()
                }
            )
            cloudSyncInProgress.value = false
        }
    }

    private fun triggerCloudRestore() {
        viewModelScope.launch {
            cloudSyncInProgress.value = true
            cloudSyncError.value = null
            cloudSyncRepository.pullAll().fold(
                ifLeft = { error -> cloudSyncError.value = error },
                ifRight = {
                    cloudSyncLastSyncedEpochMs.longValue = System.currentTimeMillis()
                }
            )
            cloudSyncInProgress.value = false
        }
    }

    private fun setCurrency(newCurrency: String) {
        currencyCode.value = newCurrency

        viewModelScope.launch {
            ioThread {
                settingsWriter.save(
                    settingsDao.findFirst().copy(
                        currency = newCurrency
                    )
                )
                AssetCode.from(newCurrency).onRight {
                    syncExchangeRatesUseCase.sync(it)
                }
            }
        }
    }

    private fun setName(newName: String) {
        name.value = newName

        viewModelScope.launch {
            ioThread {
                settingsWriter.save(
                    settingsDao.findFirst().copy(
                        name = newName
                    )
                )
            }
        }
    }

    private fun exportToCSV(rootScreen: RootScreen) {
        ivyContext.createNewFile(
            "IvyWalletExport_${
                timeNowUTC().getISOFormattedDateTime()
            }.csv"
        ) { fileUri ->
            viewModelScope.launch {
                exportCsvUseCase.exportToFile(
                    outputFile = fileUri
                )

                rootScreen.shareCSVFile(
                    fileUri = fileUri
                )
            }
        }
    }

    private fun exportToZip(rootScreen: RootScreen) {
        ivyContext.createNewFile(
            "IvyWalletBackup_${
                timeNowUTC().getISOFormattedDateTime()
            }.zip"
        ) { fileUri ->
            viewModelScope.launch(Dispatchers.IO) {
                progressState.value = true
                backupDataUseCase.exportToFile(zipFileUri = fileUri)
                progressState.value = false

                sharedPrefs.putBoolean(SharedPrefs.DATA_BACKUP_COMPLETED, true)
                ivyContext.dataBackupCompleted = true

                uiThread {
                    rootScreen.shareZipFile(
                        fileUri = fileUri
                    )
                }
            }
        }
    }

    private fun switchTheme() {
        viewModelScope.launch {
            settingsAct.getSettingsWithNextTheme().run {
                updateSettingsAct(this)
                ivyContext.switchTheme(this.theme)
                currentTheme.value = this.theme
            }
        }
    }

    private fun setLockApp(lock: Boolean) {
        lockApp.value = lock

        viewModelScope.launch {
            sharedPrefs.putBoolean(SharedPrefs.APP_LOCK_ENABLED, lock)
            refreshWidget(WalletBalanceWidgetReceiver::class.java)
        }
    }

    private fun setShowNotifications(notificationsShow: Boolean) {
        showNotifications.value = notificationsShow

        viewModelScope.launch {
            sharedPrefs.putBoolean(SharedPrefs.SHOW_NOTIFICATIONS, notificationsShow)
        }
    }

    private fun setHideCurrentBalance(hideBalance: Boolean) {
        hideCurrentBalance.value = hideBalance

        viewModelScope.launch {
            sharedPrefs.putBoolean(SharedPrefs.HIDE_CURRENT_BALANCE, hideBalance)
        }
    }

    private fun setHideIncome(isHideIncome: Boolean) {
        hideIncome.value = isHideIncome

        viewModelScope.launch {
            sharedPrefs.putBoolean(SharedPrefs.HIDE_INCOME, isHideIncome)
        }
    }

    private fun setTransfersAsIncomeExpense(setTransfersAsIncomeExpense: Boolean) {
        treatTransfersAsIncomeExpense.value = setTransfersAsIncomeExpense

        viewModelScope.launch {
            sharedPrefs.putBoolean(
                SharedPrefs.TRANSFERS_AS_INCOME_EXPENSE,
                treatTransfersAsIncomeExpense.value
            )
        }
    }

    private fun setStartDateOfMonth(startDate: Int) {
        viewModelScope.launch {
            when (val res = updateStartDayOfMonthAct(startDate)) {
                is Res.Err -> {}
                is Res.Ok -> {
                    startDateOfMonth.intValue = res.data
                }
            }
        }
    }

    private fun deleteCloudUserData() {
        viewModelScope.launch {
            cloudLogout()
        }
    }

    private fun cloudLogout() {
        viewModelScope.launch {
            logoutLogic.cloudLogout()
        }
    }

    private fun deleteAllUserData() {
        viewModelScope.launch {
            logout()
        }
    }

    private fun logout() {
        viewModelScope.launch {
            logoutLogic.logout()
        }
    }

    private fun switchLanguage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.applicationContext.startActivity(intent)
        }
    }
}
