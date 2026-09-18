package com.veljkoni.chessko.logic

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.veljkoni.chessko.R

class SoundManager(private val context: Context) {
    private var soundPool: SoundPool? = null
    private var moveSoundId: Int = 0
    private var captureSoundId: Int = 0

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        soundPool?.let { pool ->
            // Android SDK allows loading raw resources directly into SoundPool
            moveSoundId = pool.load(context, R.raw.move_self, 1)
            captureSoundId = pool.load(context, R.raw.capture, 1)
        }
    }

    fun playMove() {
        if (!SettingsManager.getInstance(context).soundEnabled) return
        soundPool?.play(moveSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playCapture() {
        if (!SettingsManager.getInstance(context).soundEnabled) return
        soundPool?.play(captureSoundId, 1f, 1f, 1, 0, 1f)
    }

    /** Distinct check sound: capture sound at higher pitch for urgency */
    fun playCheck() {
        if (!SettingsManager.getInstance(context).soundEnabled) return
        soundPool?.play(captureSoundId, 1f, 1f, 2, 0, 1.3f)
    }

    /** Distinct checkmate sound: two rapid capture sounds for finality */
    fun playCheckmate() {
        if (!SettingsManager.getInstance(context).soundEnabled) return
        soundPool?.play(captureSoundId, 1f, 1f, 2, 0, 0.8f)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }

    /**
     * Da li je [release] vec pozvan. Cisto posmatracko svojstvo (ne menja
     * ponasanje) -- postoji da bi test mogao da dokaze da se `SoundPool`
     * stvarno oslobodio, bez pretpostavke da poziv metode znaci i stvaran
     * efekat (vidi `MainActivitySoundLifecycleTest`).
     */
    val isReleased: Boolean
        get() = soundPool == null
}
