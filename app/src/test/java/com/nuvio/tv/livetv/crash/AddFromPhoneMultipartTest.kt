package com.nuvio.tv.livetv.crash

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Reading a guide sent from a phone.
 *
 * The file arrives as the raw request body and is copied straight to disk, so it has to stop
 * exactly where the request says: a short read would save a truncated guide, and over-reading
 * would run into whatever follows on the connection.
 */
class AddFromPhoneUploadTest {

    private fun readAll(stream: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = stream.read(buffer, 0, buffer.size)
            if (n <= 0) break
            out.write(buffer, 0, n)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    @Test
    fun `the whole body is read when it is exactly the stated length`() {
        val guide = "<tv><channel id=\"a\"/></tv>"
        val stream = AddFromPhoneServer.LimitedStream(
            ByteArrayInputStream(guide.toByteArray()), guide.length.toLong()
        )

        assertEquals(guide, readAll(stream))
    }

    @Test
    fun `nothing beyond the stated length is read`() {
        // A body is followed by nothing in this server, but the limit is what keeps a stray
        // trailing byte from ending up inside the saved guide.
        val guide = "<tv/>"
        val withTrailing = guide + "TRAILING"
        val stream = AddFromPhoneServer.LimitedStream(
            ByteArrayInputStream(withTrailing.toByteArray()), guide.length.toLong()
        )

        assertEquals(guide, readAll(stream))
    }

    @Test
    fun `a body shorter than stated ends cleanly instead of hanging`() {
        val guide = "<tv/>"
        val stream = AddFromPhoneServer.LimitedStream(
            ByteArrayInputStream(guide.toByteArray()), 9999L
        )

        assertEquals(guide, readAll(stream))
    }

    @Test
    fun `a large guide streams through in pieces`() {
        val guide = "<programme>".repeat(100_000)
        val bytes = guide.toByteArray()
        val stream = AddFromPhoneServer.LimitedStream(ByteArrayInputStream(bytes), bytes.size.toLong())

        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val n = stream.read(buffer, 0, buffer.size)
            if (n <= 0) break
            out.write(buffer, 0, n)
        }

        assertEquals(bytes.size, out.size())
        assertEquals(guide, String(out.toByteArray(), Charsets.UTF_8))
    }

    @Test
    fun `a single byte read at a time stops at the limit`() {
        val stream = AddFromPhoneServer.LimitedStream(
            ByteArrayInputStream("abcdef".toByteArray()), 3L
        )
        val out = StringBuilder()
        while (true) {
            val b = stream.read()
            if (b < 0) break
            out.append(b.toChar())
        }

        assertEquals("abc", out.toString())
    }
}
