package com.github.max_larsen_swe.android.theincredibleflick.backend

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

object DownloadManager {
    val TAG: String = this::class.java.simpleName
    private lateinit var cacheDir: String
    private val mutex: Mutex = Mutex()
    private val downloader: OkHttpClient = OkHttpClient()

    fun initialize(cacheDir: String) {
        DownloadManager.cacheDir = cacheDir
    }

    private fun buildCachedUri(fileUrl: String): String {
        return Paths.get(cacheDir, fileUrl).toString()
    }

    private fun urlToDownloadPathString(url: String, suffix: String? = null): String {
        val prefix: String = "APKML_download__"
        return prefix + (if (suffix == null) hashUrl(url) else hashUrl(url) + suffix)
    }

    private fun hashUrl(url: String): String {
        val bytes = url.toByteArray()
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun runDownload(remoteUri: String, localUri: String): Boolean {
        val request = Request.Builder()
            .url(remoteUri)
            .addHeader(
                "Authorization",
                "Bearer ${Keys.API}"
            ).build()
        downloader.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download file: $response")
                return false
            }
            response.body?.byteStream()?.use { input ->
                FileOutputStream(localUri).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return true
    }

    suspend fun getCachedOrDownloadedLocalUri(remoteUri: String): String? {
        return withContext(Dispatchers.IO) {
            var localUri: String? = buildCachedUri(urlToDownloadPathString(remoteUri))
            mutex.withLock { //limit to only one download at a time
                if (Files.notExists(Paths.get(localUri))) {
                    if (!runDownload(remoteUri, localUri!!)) {
                        Files.deleteIfExists(Paths.get(localUri))
                        localUri = null
                    }
                }
            }
            return@withContext localUri
        }
    }
}