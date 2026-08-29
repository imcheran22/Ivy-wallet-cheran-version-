package com.ivy.wallet

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.ivy.data.datastore.DatastoreKeys
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Masks every amount in the app from the notification shade.
 *
 * The situation this exists for - someone glancing over your shoulder - gives you about a
 * second, which rules out opening the app and finding a setting.
 */
@AndroidEntryPoint
class HideAmountsTileService : TileService() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            updateTile(hidden())
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val hidden = !hidden()
            dataStore.edit { it[DatastoreKeys.HIDE_AMOUNTS] = hidden }
            updateTile(hidden)
        }
    }

    private suspend fun hidden(): Boolean =
        dataStore.data.first()[DatastoreKeys.HIDE_AMOUNTS] ?: false

    private fun updateTile(hidden: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (hidden) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
