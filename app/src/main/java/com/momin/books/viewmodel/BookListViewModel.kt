package com.momin.books.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.momin.books.data.AppDatabase
import com.momin.books.data.Book
import com.momin.books.data.BookRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BookRepository
    val books = AppDatabase.getInstance(application).bookDao().getAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        repository = BookRepository(AppDatabase.getInstance(application).bookDao())
    }

    fun delete(book: Book) = viewModelScope.launch { repository.delete(book) }
}
