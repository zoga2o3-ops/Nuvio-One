package com.nuvio.tv.core.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Plays the navigation sounds.
 *
 * SoundPool rather than MediaPlayer: these are a few kilobytes each, several can overlap when the
 * highlight is moved quickly, and a sound has to start within a frame or it feels late. Everything
 * is loaded once, up front, and played from a small pool.
 *
 * [enabled] and [volume] are pushed in from the settings flow, so the hot path (a DPAD press) never
 * touches DataStore — reading preferences per key press would be far too slow and would run on the
 * main thread.
 */
object UiSoundPlayer {
    private const val TAG = "UiSoundPlayer"
    private const val MAX_STREAMS = 4

    /** Set by the settings observer in MainActivity. */
    @Volatile var enabled: Boolean = false
    @Volatile var volume: Float = 0.7f

    @Volatile private var soundId: Int = 0
    @Volatile private var current: UiSound = UiSound.DEFAULT

    private var pool: SoundPool? = null
    private val ready = AtomicBoolean(false)
    /** When something last played, so a burst of held-key repeats can't turn into a rattle. */
    @Volatile private var lastPlayedAt = 0L

    /** The whole point is that held-key repeats are silent; the first press of each move plays. */
    private const val MIN_GAP_MS = 45L

    fun init(context: Context) {
        if (ready.get()) return
        val app = context.applicationContext
        runCatching {
            val p = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // SONIFICATION: the system mixes these with media and never takes focus.
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            pool = p
            current = UiSound.DEFAULT
            soundId = p.load(app, current.resId, 1)
            p.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0 && sampleId == soundId) ready.set(true)
                else if (status != 0) Log.w(TAG, "sound $sampleId failed to load: $status")
            }
        }.onFailure { Log.w(TAG, "couldn't start the sound pool", it) }
    }

    /** The sound chosen in settings changed: load the new one. */
    fun setSound(context: Context, sound: UiSound) {
        if (sound == current && ready.get()) return
        current = sound
        val p = pool ?: return
        runCatching {
            // Loading is async; the previous sound keeps working until this one is ready.
            soundId = p.load(context.applicationContext, sound.resId, 1)
        }.onFailure { Log.w(TAG, "couldn't load ${sound.id}", it) }
    }

    /** Plays the chosen sound, if sounds are on. Safe to call on every focus change. */
    fun play() {
        if (!enabled) return
        val p = pool ?: return
        val id = soundId
        if (id == 0 || !ready.get()) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPlayedAt < MIN_GAP_MS) return
        lastPlayedAt = now
        runCatching { p.play(id, volume, volume, 1, 0, 1f) }
    }

    /**
     * Plays a specific sound once, whatever is chosen and whether or not navigation sounds are on.
     * Used by the picker in Settings so each option can be heard before choosing it.
     */
    fun preview(context: Context, sound: UiSound, volume: Float = this.volume) {
        val p = pool ?: return
        runCatching {
            val id = p.load(context.applicationContext, sound.resId, 1)
            p.setOnLoadCompleteListener { sp, sampleId, status ->
                if (status == 0 && sampleId == id) sp.play(id, volume, volume, 1, 0, 1f)
            }
        }.onFailure { Log.w(TAG, "couldn't preview ${sound.id}", it) }
    }

    fun release() {
        runCatching { pool?.release() }
        pool = null
        ready.set(false)
    }
}
