package com.nuvio.tv.youtube

import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Serves a video's DASH manifest on the loopback address.
 *
 * YouTube's separate video and audio streams reach the player as a generated DASH manifest. It is
 * too big to carry in a navigation route and the player wants a URL, so the manifest is held here
 * and handed out as `http://127.0.0.1:<port>/yt/<videoId>.mpd`. Only the device itself can reach it.
 *
 * It also serves the auto-translated captions. YouTube refuses to hand those out without a token only its own
 * apps can make (HTTP 429), so when a translated language is picked, the video's own captions are fetched and
 * translated here instead.
 */
object YouTubeMpdServer {
    private const val TAG = "YouTubeCaptions"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    /** How much text goes to the translator in one request. */
    private const val CHUNK_CHARS = 4500
    /** How many translation requests may run at once. */
    private const val MAX_PARALLEL = 6

    private val manifests = ConcurrentHashMap<String, ByteArray>()
    private val pool = Executors.newCachedThreadPool()

    @Volatile private var server: ServerSocket? = null

    /** Stores [mpd] and returns the URL to play it from. */
    @Synchronized
    fun publish(videoId: String, mpd: ByteArray): String {
        // Kept at least as long as the view model keeps its cached lookups.
        if (manifests.size > 64) manifests.clear()
        manifests[videoId] = rewriteTranslatedCaptions(videoId, mpd)
        return "http://127.0.0.1:${ensureStarted().localPort}/yt/$videoId.mpd"
    }

    // ---- translated captions ----

    /** Each video's translated caption addresses, in the order they were swapped out of the manifest. */
    private val captionUrls = ConcurrentHashMap<String, List<String>>()
    private val translatedCache = ConcurrentHashMap<String, ByteArray>()
    private val http by lazy { OkHttpClient.Builder().followRedirects(true).build() }

    private fun rewriteTranslatedCaptions(videoId: String, mpd: ByteArray): ByteArray {
        val port = ensureStarted().localPort
        val urls = ArrayList<String>()
        val rewritten = Regex("<BaseURL>([^<]*timedtext[^<]*)</BaseURL>").replace(String(mpd, Charsets.UTF_8)) { m ->
            val url = m.groupValues[1].replace("&amp;", "&")
            if ("tlang=" !in url) m.value
            else {
                urls += url
                "<BaseURL>http://127.0.0.1:$port/yt/cap/$videoId/${urls.size - 1}</BaseURL>"
            }
        }
        captionUrls[videoId] = urls
        if (translatedCache.size > 12) translatedCache.clear()
        return rewritten.toByteArray(Charsets.UTF_8)
    }

    private fun fetchTranslatedCaption(videoId: String, index: Int): Pair<ByteArray, String>? {
        val address = captionUrls[videoId]?.getOrNull(index) ?: return null
        val language = Regex("[?&]tlang=([^&]+)").find(address)?.groupValues?.get(1) ?: return null
        val key = "$videoId/$language"
        translatedCache[key]?.let { return it to typeOf(it) }
        val began = System.currentTimeMillis()
        // The video's own captions are served without any token. Fetch those and translate them.
        val original = address.replace(Regex("&tlang=[^&]+"), "")
        val source = get(original) ?: return null
        val translated = translateCaptions(source, language) ?: return null
        val bytes = translated.toByteArray(Charsets.UTF_8)
        translatedCache[key] = bytes
        Log.i(TAG, "captions for $key ready in ${System.currentTimeMillis() - began} ms")
        return bytes to typeOf(bytes)
    }

    private fun typeOf(body: ByteArray) = if (String(body, 0, minOf(body.size, 8), Charsets.UTF_8).startsWith("WEBVTT")) "text/vtt" else "application/ttml+xml"

    private fun get(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use {
            if (it.isSuccessful) it.body?.string() else {
                Log.w(TAG, "caption request -> HTTP ${it.code}")
                null
            }
        }
    }.onFailure { Log.w(TAG, "caption request failed", it) }.getOrNull()

    private val cue = Regex("(<p\\b[^>]*>)(.*?)(</p>)", RegexOption.DOT_MATCHES_ALL)

    /** Translates the text of every cue in a caption file (WebVTT or TTML), keeping the timing. */
    private fun translateCaptions(file: String, language: String): String? =
        (if (file.trimStart().startsWith("WEBVTT")) translateVtt(file, language) else translateTtml(file, language))
            .also { if (it == null) Log.w(TAG, "couldn't translate the captions to $language") }

    private fun translateVtt(vtt: String, language: String): String? {
        val blocks = vtt.replace("\r\n", "\n").split(Regex("\n{2,}"))
        // Each cue is a block with a timing line; its text is whatever follows that line.
        val timingIndex = blocks.map { block -> block.lines().indexOfFirst { "-->" in it } }
        val texts = blocks.indices.mapNotNull { i ->
            val t = timingIndex[i]
            if (t < 0) null else plainText(blocks[i].lines().drop(t + 1).joinToString(" "))
        }
        if (texts.none { it.isNotBlank() }) return null
        val translated = translateAll(texts, language) ?: return null
        var n = 0
        val rebuilt = blocks.indices.map { i ->
            val t = timingIndex[i]
            if (t < 0) blocks[i]
            else (blocks[i].lines().take(t + 1) + translated[n++]).joinToString("\n")
        }.joinToString("\n\n")
        Log.i(TAG, "translated ${texts.size} caption cues to $language")
        return rebuilt + "\n"
    }

    private fun translateTtml(ttml: String, language: String): String? {
        val cues = cue.findAll(ttml).toList()
        if (cues.isEmpty()) return null
        val translated = translateAll(cues.map { plainText(it.groupValues[2]) }, language) ?: return null
        var n = 0
        val rebuilt = cue.replace(ttml) { m -> m.groupValues[1] + escape(translated[n++]) + m.groupValues[3] }
        Log.i(TAG, "translated ${cues.size} caption cues to $language")
        return rebuilt
    }

    /**
     * Translates every text. YouTube's automatic captions repeat each line as the words roll in, so only the
     * different lines are sent, and the requests go out side by side instead of one after another.
     */
    private fun translateAll(texts: List<String>, language: String): List<String>? {
        val unique = texts.filter { it.isNotBlank() }.distinct()
        val chunks = ArrayList<List<String>>()
        var start = 0
        while (start < unique.size) {
            var end = start
            var chars = 0
            while (end < unique.size && (chars == 0 || chars + unique[end].length < CHUNK_CHARS)) {
                chars += unique[end].length + 1
                end++
            }
            chunks += unique.subList(start, end)
            start = end
        }
        val began = System.currentTimeMillis()
        val workers = Executors.newFixedThreadPool(minOf(MAX_PARALLEL, maxOf(chunks.size, 1)))
        val translated = try {
            chunks.map { chunk -> workers.submit<List<String>?> { translateChunk(chunk, language) } }
                .map { it.get() ?: return null }
        } finally {
            workers.shutdown()
        }
        val byText = HashMap<String, String>()
        chunks.forEachIndexed { i, chunk -> chunk.forEachIndexed { j, text -> byText[text] = translated[i][j] } }
        Log.i(TAG, "translated ${unique.size} different lines (of ${texts.size}) in ${chunks.size} request(s), ${System.currentTimeMillis() - began} ms")
        return texts.map { if (it.isBlank()) it else byText[it].orEmpty() }
    }

    /** Translates [lines] together; falls back to one at a time if the translator merges or splits lines. */
    private fun translateChunk(lines: List<String>, language: String): List<String>? {
        val joined = translate(lines.joinToString("\n"), language)?.split("\n")
        if (joined != null && joined.size == lines.size) return joined
        Log.w(TAG, "lines didn't line up (${joined?.size} vs ${lines.size}); translating one by one")
        return lines.map { if (it.isBlank()) it else translate(it, language) ?: return null }
    }

    private fun translate(text: String, language: String): String? = runCatching {
        val target = when (language) {
            "zh-Hans" -> "zh-CN"
            "zh-Hant" -> "zh-TW"
            "iw" -> "he"
            else -> language
        }
        val body = FormBody.Builder().add("q", text).build()
        val request = Request.Builder()
            .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t")
            .header("User-Agent", USER_AGENT)
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "translate -> HTTP ${response.code}")
                return@runCatching null
            }
            val parts = JSONArray(response.body?.string().orEmpty()).getJSONArray(0)
            buildString { for (i in 0 until parts.length()) append(parts.getJSONArray(i).optString(0)) }
        }
    }.onFailure { Log.w(TAG, "translate failed", it) }.getOrNull()

    private fun plainText(inner: String): String =
        inner.replace(Regex("<br\\s*/?>"), " ").replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace(Regex("\\s+"), " ").trim()

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ---- the server ----

    private fun ensureStarted(): ServerSocket {
        server?.takeIf { !it.isClosed }?.let { return it }
        val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        server = socket
        Thread({
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                pool.execute { runCatching { handle(client) } }
            }
        }, "yt-mpd-server").apply { isDaemon = true }.start()
        return socket
    }

    private fun reply(out: java.io.OutputStream, status: String, type: String?, body: ByteArray?) {
        val head = StringBuilder("HTTP/1.1 $status\r\n")
        if (type != null) head.append("Content-Type: $type\r\n")
        head.append("Content-Length: ${body?.size ?: 0}\r\nConnection: close\r\n\r\n")
        out.write(head.toString().toByteArray())
        if (body != null) out.write(body)
        out.flush()
    }

    private fun handle(client: Socket) = client.use { c ->
        val request = c.getInputStream().bufferedReader().readLine().orEmpty()
        val out = c.getOutputStream()
        val caption = Regex("""GET /yt/cap/([^/ ]+)/(\d+)""").find(request)
        if (caption != null) {
            val result = fetchTranslatedCaption(caption.groupValues[1], caption.groupValues[2].toInt())
            if (result == null) reply(out, "404 Not Found", null, null) else reply(out, "200 OK", result.second, result.first)
            return@use
        }
        val id = Regex("""GET /yt/([^./ ]+)\.mpd""").find(request)?.groupValues?.get(1)
        val body = id?.let { manifests[it] }
        if (body == null) reply(out, "404 Not Found", null, null) else reply(out, "200 OK", "application/dash+xml", body)
    }
}
