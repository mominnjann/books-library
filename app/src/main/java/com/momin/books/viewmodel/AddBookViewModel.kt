package com.momin.books.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.momin.books.data.AppDatabase
import com.momin.books.data.Book
import com.momin.books.data.BookRepository
import com.momin.books.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class AddBookViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BookRepository = BookRepository(AppDatabase.getInstance(application).bookDao())

    /**
     * Copy the file referenced by [uri] into app storage, optionally create cover, and insert metadata.
     * Returns newly inserted book id or -1 on failure
     */
    fun addBookFromUri(uri: Uri, title: String, author: String, genre: String?, onResult: (Long) -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            try {
                val fileNameHint = uri.lastPathSegment?.replace("/", "_") ?: "book_${System.currentTimeMillis()}"
                val path = withContext(Dispatchers.IO) {
                    FileUtil.copyUriToInternal(context, uri, fileNameHint)
                }
                // Try to create a PDF cover if PDF
                var coverPath: String? = null
                if (path.endsWith(".pdf", ignoreCase = true)) {
                    coverPath = FileUtil.createPdfCover(context, path, "cover_${System.currentTimeMillis()}.png")
                }

                val book = Book(
                    title = title,
                    author = author,
                    genre = genre,
                    filePath = path,
                    coverUri = coverPath
                )
                val id = withContext(Dispatchers.IO) { repository.insert(book) }
                onResult(id)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(-1)
            }
        }
    }
}
