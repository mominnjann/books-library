package com.momin.books.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileUtil {
    /**
     * Copy a document referenced by [uri] into app internal files directory and return absolute path
     */
    fun copyUriToInternal(context: Context, uri: Uri, destFileNameHint: String? = null): String {
        val resolver: ContentResolver = context.contentResolver

        // Determine filename from content resolver (OpenableColumns) or fallback
        var fileName: String? = null
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                fileName = cursor.getString(0)
            }
        }
        if (fileName.isNullOrBlank()) {
            fileName = uri.lastPathSegment?.substringAfterLast('/')
        }

        val resolvedName = if (!fileName.isNullOrBlank()) fileName!! else (destFileNameHint ?: "book_${System.currentTimeMillis()}")

        // Ensure an extension exists; if missing, try to get from mime type
        val nameWithExt = if (resolvedName.contains('.')) {
            resolvedName
        } else {
            val mime = resolver.getType(uri)
            val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            if (!ext.isNullOrBlank()) "$resolvedName.$ext" else resolvedName
        }

        val input: InputStream = resolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open URI")
        val dest = File(context.filesDir, nameWithExt)
        FileOutputStream(dest).use { out ->
            input.copyTo(out)
        }
        input.close()
        return dest.absolutePath
    }

    /** Render first page of PDF at [pdfPath] into a PNG file and return its absolute path */
    fun createPdfCover(context: Context, pdfPath: String, outFileName: String): String? {
        val file = File(pdfPath)
        if (!file.exists()) return null
        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val width = page.width
                    val height = page.height
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val outFile = File(context.filesDir, outFileName)
                    FileOutputStream(outFile).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 85, fos)
                    }
                    return outFile.absolutePath
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pfd?.close()
        }
        return null
    }
}
