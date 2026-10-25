package com.reatv.launcher.data.shelf

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The few HTTPS GETs the Top Shelf makes, through the platform's HttpURLConnection (no client
 * library), with an on-disk cache in the app's cache directory that Android may clear at will.
 * Blocking: call from a background dispatcher.
 */
class Http(private val dir: File, private val maxBytes: Long) {
    private var lastTrim = 0L

    /** The body as text, from cache when younger than [maxAgeMs]. Null on any failure. */
    fun text(url: String, maxAgeMs: Long, headers: Map<String, String> = emptyMap()): String? {
        val file = fetch(url, maxAgeMs, headers, maxBody = 2L shl 20) ?: return null
        return try {
            file.readText()
        } catch (e: IOException) {
            null
        }
    }

    /** The cached file holding the body (images), or null. */
    fun file(url: String, maxAgeMs: Long): File? = fetch(url, maxAgeMs, emptyMap(), maxBody = 8L shl 20)

    private fun fetch(url: String, maxAgeMs: Long, headers: Map<String, String>, maxBody: Long): File? {
        if (!url.startsWith("https://")) return null
        val file = File(dir, sha1(url))
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < maxAgeMs) return file
        val fresh = download(url, headers, file, maxBody)
        // Offline or failing: a stale copy beats nothing.
        return fresh ?: file.takeIf { it.exists() }
    }

    private fun download(url: String, headers: Map<String, String>, target: File, maxBody: Long): File? {
        var connection: HttpURLConnection? = null
        return try {
            dir.mkdirs()
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "REA-TV")
                for ((k, v) in headers) setRequestProperty(k, v)
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val tmp = File(dir, target.name + ".part")
            var total = 0L
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > maxBody) throw IOException("too large")
                        output.write(buffer, 0, n)
                    }
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return null
            }
            trimSoon()
            target
        } catch (e: Exception) {
            File(dir, target.name + ".part").delete()
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Oldest files go first once the cache passes [maxBytes]; checked at most once a minute. */
    private fun trimSoon() {
        val now = System.currentTimeMillis()
        if (now - lastTrim < 60_000) return
        lastTrim = now
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes * 3 / 4) break
            total -= f.length()
            f.delete()
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
