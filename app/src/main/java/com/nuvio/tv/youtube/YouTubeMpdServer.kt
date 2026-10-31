package com.nuvio.tv.youtube

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
 */
object YouTubeMpdServer {
    private val manifests = ConcurrentHashMap<String, ByteArray>()
    private val pool = Executors.newCachedThreadPool()

    @Volatile private var server: ServerSocket? = null

    /** Stores [mpd] and returns the URL to play it from. */
    @Synchronized
    fun publish(videoId: String, mpd: ByteArray): String {
        // Kept at least as long as the view model keeps its cached lookups.
        if (manifests.size > 64) manifests.clear()
        manifests[videoId] = mpd
        return "http://127.0.0.1:${ensureStarted().localPort}/yt/$videoId.mpd"
    }

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

    private fun handle(client: Socket) = client.use { c ->
        val request = c.getInputStream().bufferedReader().readLine().orEmpty()
        val id = Regex("""GET /yt/([^./ ]+)\.mpd""").find(request)?.groupValues?.get(1)
        val body = id?.let { manifests[it] }
        val out = c.getOutputStream()
        if (body == null) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        } else {
            out.write(
                ("HTTP/1.1 200 OK\r\nContent-Type: application/dash+xml\r\n" +
                    "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray()
            )
            out.write(body)
        }
        out.flush()
    }
}
