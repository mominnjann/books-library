package com.momin.books.backup

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Helper API wrapper for Drive REST calls to simplify testing.
 * - `baseUrl` is injectable for tests (use MockWebServer)
 * - `onUnauthorized` will be called when a 401 is received so callers can clear tokens
 */
class DriveApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://www.googleapis.com/drive/v3",
    private val onUnauthorized: (() -> Unit)? = null
) {

    fun listBackups(token: String): List<DriveFileItem> {
        val q = "name contains 'books_export' and mimeType='application/zip'"
        val url = "$baseUrl/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&orderBy=createdTime%20desc&fields=files(id,name,createdTime,size)"
        val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").get().build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 401) onUnauthorized?.invoke()
                return emptyList()
            }
            val body = resp.body?.string() ?: return emptyList()
            // reuse parsing util
            return parseDriveFilesJson(body).map { DriveFileItem(it.id, it.name, it.createdTime, it.size) }
        }
    }

    fun downloadFile(token: String, fileId: String, dest: File): Boolean {
        val url = "$baseUrl/files/$fileId?alt=media"
        val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").get().build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 401) onUnauthorized?.invoke()
                return false
            }
            resp.body?.byteStream()?.use { ins ->
                dest.outputStream().use { outs -> ins.copyTo(outs) }
            }
            return true
        }
    }
}
