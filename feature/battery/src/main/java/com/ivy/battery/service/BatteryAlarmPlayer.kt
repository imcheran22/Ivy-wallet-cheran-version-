package com.ivy.battery.service

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the looping alarm tone + vibration that accompanies a charge, low
 * battery or overheating alert. Kept separate from the notification so the
 * alarm can be silenced without dismissing the notification (and vice versa).
 */
@Singleton
class BatteryAlarmPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var ringtone: Ringtone? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    @Synchronized
    fun start(sound: Boolean, vibrate: Boolean) {
        if (sound) startTone()
        if (vibrate) startVibration()
    }

    @Synchronized
    fun stop() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
    }

    private fun startTone() {
        if (ringtone?.isPlaying == true) return
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                isLooping = true
                play()
            }
        }.onFailure { Timber.w(it, "Battery: could not play alarm tone") }
    }

    private fun startVibration() {
        runCatching {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, VIBRATION_REPEAT_INDEX)
            )
        }.onFailure { Timber.w(it, "Battery: could not vibrate") }
    }

    companion object {
        private val VIBRATION_PATTERN = longArrayOf(0, 400, 600)
        private const val VIBRATION_REPEAT_INDEX = 0
    }
}
