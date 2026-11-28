package com.nuvio.tv.music

import android.content.Context
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Emby and Jellyfin music for the Music section.
 *
 * Both servers speak the same API — Jellyfin is a fork of Emby — so one client covers both. Emby
 * answers under `/emby`, Jellyfin under the bare root, which is the only difference in addressing.
 *
 * Why this exists: the app already browsed Emby and Jellyfin for **video**, but a music library was
 * dropped on the way in (the library reader only knew movies, TV and box sets), and nothing asked
 * the server for `Audio` items. So a person with their music on Emby could play their films and not
 * one track.
 *
 * Video libraries stay where they are; this only ever asks for audio.
 */
data class EmbyMusicConfig(
    val server: String = "",
    val isJellyfin: Boolean = false,
    val userId: String = "",
    val token: String = "",
) {
    val configured: Boolean
        get() = server.isNotBlank() && userId.isNotBlank() && token.isNotBlank()
}

data class EmbyAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val imageTag: String?,
    val songCount: Int,
)

object MusicEmby {
    private const val PREFS = "nuvio_music_emby"
    private const val KEY_SERVER = "server"
    private const val KEY_JELLYFIN = "jellyfin"
    private const val KEY_USER = "user"
    private const val KEY_TOKEN = "token"

    /** Emby's own authentication header. Jellyfin accepts the same name. */
    private const val AUTH_HEADER = "X-Emby-Authorization"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val _config = MutableStateFlow(EmbyMusicConfig())
    val config: StateFlow<EmbyMusicConfig> = _config.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _config.value = EmbyMusicConfig(
            server = prefs.getString(KEY_SERVER, "").orEmpty(),
            isJellyfin = prefs.getBoolean(KEY_JELLYFIN, false),
            userId = prefs.getString(KEY_USER, "").orEmpty(),
            token = prefs.getString(KEY_TOKEN, "").orEmpty(),
        )
    }

    /**
     * Signs in with a username and password and keeps the access token.
     *
     * Emby and Jellyfin both return the user id and token from `/Users/AuthenticateByName`; the
     * password is used once here and never stored.
     */
    fun connect(
        context: Context,
        server: String,
        username: String,
        password: String,
        isJellyfin: Boolean,
    ): Result<Unit> = runCatching {
        val base = normalizeServer(server)
        val body = JSONObject()
            .put("Username", username)
            .put("Pw", password)
            .toString()
        val request = Request.Builder()
            .url("$base${apiRoot(isJellyfin)}/Users/AuthenticateByName")
            .header(AUTH_HEADER, authHeaderValue(isJellyfin))
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val root = client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // Emby states the reason in the body ("Invalid username or password").
                val message = runCatching { JSONObject(text).optString("Message") }.getOrNull()
                error(message?.takeIf { it.isNotBlank() } ?: "Sign-in failed (HTTP ${response.code}).")
            }
            JSONObject(text)
        }
        val user = root.optJSONObject("User") ?: error("The server did not return a user.")
        val userId = user.optString("Id").ifBlank { error("The server returned no user id.") }
        val token = root.optString("AccessToken").ifBlank { error("The server returned no access token.") }
        save(context, EmbyMusicConfig(base, isJellyfin, userId, token))
    }

    fun disconnect(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
        _config.value = EmbyMusicConfig()
    }

    /** The server's music albums, newest first. */
    fun albums(limit: Int = 60): Result<List<EmbyAlbum>> {
        val config = _config.value
        if (!config.configured) return Result.failure(IllegalStateException("Emby isn't connected."))
        return request(
            config,
            "/Items",
            mapOf(
                "IncludeItemTypes" to "MusicAlbum",
                "Recursive" to "true",
                "SortBy" to "DateCreated,SortName",
                "SortOrder" to "Descending",
                "Limit" to limit.toString(),
                "Fields" to "ChildCount,AlbumArtist",
            ),
        ).map { root ->
            val items = root.optJSONArray("Items") ?: JSONArray()
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    add(item.toAlbum(config))
                }
            }
        }
    }

    /** One album's tracks, in the server's own track order. */
    fun albumSongs(albumId: String): Result<List<Song>> {
        val config = _config.value
        if (!config.configured) return Result.failure(IllegalStateException("Emby isn't connected."))
        return request(
            config,
            "/Items",
            mapOf(
                "ParentId" to albumId,
                "IncludeItemTypes" to "Audio",
                "Recursive" to "true",
                "SortBy" to "ParentIndexNumber,IndexNumber,SortName",
                "Fields" to "RunTimeTicks,Album,AlbumArtist",
            ),
        ).map { root ->
            val items = root.optJSONArray("Items") ?: JSONArray()
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    add(item.toSong(config) ?: continue)
                }
            }
        }
    }

    /**
     * A search across the server's music.
     *
     * Emby has no audio-only search endpoint, so this asks for audio items with the usual search
     * parameter and relies on [IncludeItemTypes] to keep films out of the result.
     */
    fun search(query: String, limit: Int = 40): Result<List<Song>> {
        val config = _config.value
        if (!config.configured) return Result.failure(IllegalStateException("Emby isn't connected."))
        if (query.isBlank()) return Result.success(emptyList())
        return request(
            config,
            "/Items",
            mapOf(
                "IncludeItemTypes" to "Audio",
                "Recursive" to "true",
                "SearchTerm" to query,
                "Limit" to limit.toString(),
                "Fields" to "RunTimeTicks,Album,AlbumArtist",
            ),
        ).map { root ->
            val items = root.optJSONArray("Items") ?: JSONArray()
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    add(item.toSong(config) ?: continue)
                }
            }
        }
    }

    /** Direct stream URL for a track, for the player to open. */
    fun streamUrl(song: Song): String? {
        val config = _config.value
        if (!config.configured) return null
        val id = song.videoId.removePrefix(PREFIX)
        if (id.isBlank()) return null
        // Static=true asks for the file as it is, with no transcoding. Music is small; playing the
        // original keeps quality and avoids the server having to work.
        return "${config.server}${apiRoot(config.isJellyfin)}/Audio/$id/stream?static=true" +
            "&api_key=${encode(config.token)}&DeviceId=${encode(DEVICE_ID)}"
    }

    fun imageUrl(album: EmbyAlbum, size: Int = 480): String? {
        val config = _config.value
        if (!config.configured || album.imageTag.isNullOrBlank()) return null
        return "${config.server}${apiRoot(config.isJellyfin)}/Items/${album.id}/Images/Primary" +
            "?maxHeight=$size&tag=${encode(album.imageTag)}&api_key=${encode(config.token)}"
    }

    // ------------------------------------------------------------------ internals

    private fun save(context: Context, config: EmbyMusicConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER, config.server)
            .putBoolean(KEY_JELLYFIN, config.isJellyfin)
            .putString(KEY_USER, config.userId)
            .putString(KEY_TOKEN, config.token)
            .apply()
        _config.value = config
    }

    private fun request(
        config: EmbyMusicConfig,
        path: String,
        params: Map<String, String>,
    ): Result<JSONObject> = runCatching {
        val query = params.entries.joinToString("&") { "${it.key}=${encode(it.value)}" }
        val url = "${config.server}${apiRoot(config.isJellyfin)}$path" +
            "?userId=${encode(config.userId)}&api_key=${encode(config.token)}" +
            if (query.isBlank()) "" else "&$query"
        val request = Request.Builder()
            .url(url)
            .header(AUTH_HEADER, authHeaderValue(config.isJellyfin, config.token))
            .header("User-Agent", "NuvioOne/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("The server returned HTTP ${response.code}.")
            }
            JSONObject(body)
        }
    }

    private fun JSONObject.toAlbum(config: EmbyMusicConfig): EmbyAlbum = EmbyAlbum(
        id = optString("Id"),
        name = optString("Name").ifBlank { "Unknown album" },
        // AlbumArtist is the album's credit; Artists is the per-track list and is an array.
        artist = optString("AlbumArtist").ifBlank { optJSONArray("Artists")?.optString(0).orEmpty() },
        // Emby sends "ImageTags": {"Primary": "<tag>"} — an object, not an array.
        imageTag = optJSONObject("ImageTags")?.optString("Primary")?.takeIf { it.isNotBlank() },
        songCount = optInt("ChildCount", 0),
    )

    private fun JSONObject.toSong(config: EmbyMusicConfig): Song? {
        val id = optString("Id").ifBlank { return null }
        val ticks = optLong("RunTimeTicks", 0L)
        val seconds = (ticks / 10_000_000L).toInt()
        return Song(
            videoId = PREFIX + id,
            title = optString("Name").ifBlank { "Unknown track" },
            artist = optString("AlbumArtist")
                .ifBlank { optJSONArray("Artists")?.optString(0).orEmpty() },
            thumbnailUrl = optJSONObject("ImageTags")?.optString("Primary")
                ?.takeIf { it.isNotBlank() }
                ?.let { "${config.server}${apiRoot(config.isJellyfin)}/Items/$id/Images/Primary?tag=$it&api_key=${encode(config.token)}" },
            durationText = if (seconds > 0) "%d:%02d".format(seconds / 60, seconds % 60) else null,
            albumName = optString("Album").takeIf { it.isNotBlank() },
            // Played straight from the server, the same way Navidrome tracks are.
            localUri = "${config.server}${apiRoot(config.isJellyfin)}/Audio/$id/stream?static=true" +
                "&api_key=${encode(config.token)}",
        )
    }

    private fun apiRoot(isJellyfin: Boolean) = if (isJellyfin) "/" else "/emby"

    private fun normalizeServer(server: String): String {
        val trimmed = server.trim().trimEnd('/')
        // A bare host is a common paste; Emby itself is served over http on the LAN by default.
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "http://$trimmed"
    }

    private fun authHeaderValue(isJellyfin: Boolean, token: String = ""): String {
        val client = if (isJellyfin) "NuvioOne" else "NuvioOne"
        return buildString {
            append("MediaBrowser Client=\"$client\", Device=\"Android TV\", ")
            append("DeviceId=\"$DEVICE_ID\", Version=\"1.0\"")
            if (token.isNotBlank()) append(", Token=\"$token\"")
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    /** Tracks carry this so they can be told apart from local files and Navidrome entries. */
    const val PREFIX = "emby:"
    private const val DEVICE_ID = "nuvio-one-tv"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
}
