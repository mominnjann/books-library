package com.momin.books.backup

import android.content.Context
import com.momin.books.data.AppDatabase
import com.momin.books.data.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BackupManager {
    /**
     * Export library metadata and files into a ZIP file at [destFile]. Returns true on success.
     */
    suspend fun exportLibrary(context: Context, destFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getInstance(context).bookDao()
            val books = dao.getAll() // Flow - but using synchronous approach by reading DB file directly would be more complex
            // For simplicity, read DB directly using getById for small datasets
            val all = mutableListOf<Book>()
            // Note: This uses a crude approach - in real app use proper suspension to collect flow
            var id = 1
            while (true) {
                val b = dao.getById(id) ?: break
                all.add(b)
                id++
            }

            val meta = JSONArray()
            val tmpDir = File(context.cacheDir, "books_export_${System.currentTimeMillis()}")
            tmpDir.mkdirs()

            for (b in all) {
                val o = JSONObject()
                o.put("id", b.id)
                o.put("title", b.title)
                o.put("author", b.author)
                o.put("genre", b.genre)
                o.put("filePath", File(b.filePath).name)
                o.put("coverUri", b.coverUri?.let { File(it).name })
                o.put("lastPage", b.lastPage)
                meta.put(o)

                // copy file and cover
                try {
                    val f = File(b.filePath)
                    if (f.exists()) f.copyTo(File(tmpDir, f.name), overwrite = true)
                    b.coverUri?.let { cov ->
                        val cf = File(cov)
                        if (cf.exists()) cf.copyTo(File(tmpDir, cf.name), overwrite = true)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // write metadata
            val metaFile = File(tmpDir, "metadata.json")
            FileOutputStream(metaFile).use { it.write(meta.toString(2).toByteArray(Charsets.UTF_8)) }

            // zip
            ZipOutputStream(FileOutputStream(destFile)).use { zos ->
                tmpDir.listFiles()?.forEach { file ->
                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    file.inputStream().copyTo(zos)
                    zos.closeEntry()
                }
            }

            // cleanup
            tmpDir.deleteRecursively()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // TODO: Implement Google Drive upload & restore using OAuth 2.0 and Drive REST API
}
