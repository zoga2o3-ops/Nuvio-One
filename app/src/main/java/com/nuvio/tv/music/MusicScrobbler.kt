package com.nuvio.tv.music

import android.content.Context
import android.util.Log
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class LastFmConfig(
    val apiKey: String = "",
    val secret: String = "",
    val sessionKey: String = "",
    val enabled: Boolean = false
) {
    val configured: Boolean
        get() = apiKey.isNotBlank() && secret.isNotBlank() && sessionKey.isNotBlank()
}

/**
 * Last.fm scrobbling for the Music section.
 *
 * There is no in-app Last.fm login: the API key, shared secret and a session key are pasted in
 * Integrations. A track is scrobbled once it passes half its duration (or four minutes, whichever
 * comes first) and is at least 30 seconds long.
 */
object MusicScrobbler {
    private const val TAG = "MusicScrobbler"
    private const val PREFS = "nuvio_music_lastfm"
    private const val KEY_API = "api_key"
    private const val KEY_SECRET = "secret"
    private const val KEY_SESSION = "session_key"
    private const val KEY_ENABLED = "enabled"
    private const val ENDPOINT = "https://ws.audioscrobbler.com/2.0/"

    private val _config = MutableStateFlow(LastFmConfig())
    val config: StateFlow<LastFmConfig> = _config.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var started = false
    private var announcedId: String? = null
    private var scrobbledId: String? = null
    private var lastSongId: String? = null
    private var lastPositionMs = 0L

    @Synchronized
    fun init(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _config.value = LastFmConfig(
            apiKey = prefs.getString(KEY_API, "").orEmpty(),
            secret = prefs.getString(KEY_SECRET, "").orEmpty(),
            sessionKey = prefs.getString(KEY_SESSION, "").orEmpty(),
            enabled = prefs.getBoolean(KEY_ENABLED, false)
        )
        if (!started) {
            started = true
            observePlayback()
        }
    }

    fun save(context: Context, apiKey: String, secret: String, sessionKey: String) {
        val app = context.applicationContext
        val updated = LastFmConfig(
            apiKey = apiKey.trim(),
            secret = secret.trim(),
            sessionKey = sessionKey.trim(),
            enabled = _config.value.enabled
        )
        _config.value = updated
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_API, updated.apiKey)
            .putString(KEY_SECRET, updated.secret)
            .putString(KEY_SESSION, updated.sessionKey)
            .putBoolean(KEY_ENABLED, updated.enabled)
            .apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        val updated = _config.value.copy(enabled = enabled)
        _config.value = updated
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        announcedId = null
        scrobbledId = null
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        _config.value = LastFmConfig()
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        announcedId = null
        scrobbledId = null
    }

    private fun observePlayback() {
        scope.launch {
            MusicPlayer.state.collect { state ->
                val config = _config.value
                if (!config.enabled || !config.configured) return@collect
                val song = state.song ?: return@collect
                if (song.videoId != lastSongId) {
                    lastSongId = song.videoId
                    lastPositionMs = 0L
                    announcedId = null
                    scrobbledId = null
                }
                if (state.positionMs + 5_000L < lastPositionMs) {
                    // A manual seek to the top (or repeat-one) is a fresh listen.
                    announcedId = null
                    scrobbledId = null
                }
                lastPositionMs = state.positionMs
                if (state.isPlaying && announcedId != song.videoId) {
                    announcedId = song.videoId
                    scrobbledId = null
                    updateNowPlaying(config, song)
                }
                if (scrobbledId == song.videoId || !state.isPlaying) return@collect
                val duration = state.durationMs
                if (duration < 30_000L) return@collect
                val threshold = min(duration / 2L, 4 * 60_000L)
                if (state.positionMs >= threshold) {
                    scrobbledId = song.videoId
                    scrobble(config, song)
                }
            }
        }
    }

    private fun updateNowPlaying(config: LastFmConfig, song: Song) {
        val params = linkedMapOf(
            "method" to "track.updateNowPlaying",
            "api_key" to config.apiKey,
            "sk" to config.sessionKey,
            "artist" to song.artist,
            "track" to song.title
        )
        post(config, params)
    }

    private fun scrobble(config: LastFmConfig, song: Song) {
        val params = linkedMapOf(
            "method" to "track.scrobble",
            "api_key" to config.apiKey,
            "sk" to config.sessionKey,
            "artist" to song.artist,
            "track" to song.title,
            "timestamp" to (System.currentTimeMillis() / 1000L).toString()
        )
        post(config, params)
    }

    private fun post(config: LastFmConfig, params: Map<String, String>) {
        runCatching {
            val signed = params.toMutableMap()
            signed["api_sig"] = signature(params, config.secret)
            val form = FormBody.Builder().apply {
                signed.forEach { (key, value) -> add(key, value) }
            }.build()
            val request = Request.Builder()
                .url(ENDPOINT)
                .post(form)
                .header("User-Agent", "NuvioOne/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "${params["method"]} failed: HTTP ${response.code}")
                } else {
                    val body = response.body?.string().orEmpty()
                    if (body.contains("\"error\"")) {
                        Log.w(TAG, "${params["method"]} error: $body")
                    }
                }
            }
        }.onFailure { Log.w(TAG, "${params["method"]} failed: ${it.message}") }
    }

    /**
     * Last.fm's signature is MD5 over the alphabetically sorted `key + value` pairs followed by
     * the shared secret. The `format` and `callback` keys are never part of it.
     */
    internal fun signature(params: Map<String, String>, secret: String): String {
        val raw = params.toSortedMap().entries.joinToString("") { (key, value) -> key + value } +
            secret
        return MessageDigest.getInstance("MD5")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
