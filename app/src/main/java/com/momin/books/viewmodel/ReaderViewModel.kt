package com.momin.books.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.momin.books.data.AppDatabase
import com.momin.books.data.Book
import kotlinx.coroutines.launch

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppDatabase.getInstance(application).bookDao()

    fun saveProgress(bookId: Int, lastPage: Int) {
        viewModelScope.launch {
            val book: Book? = repository.getById(bookId)
            if (book != null) {
                repository.update(book.copy(lastPage = lastPage))
            }
        }
    }
}
