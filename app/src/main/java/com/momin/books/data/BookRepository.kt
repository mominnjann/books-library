package com.momin.books.data

import kotlinx.coroutines.flow.Flow

class BookRepository(private val dao: BookDao) {
    fun getAll(): Flow<List<Book>> = dao.getAll()

    suspend fun getById(id: Int): Book? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dao.getById(id) }

    suspend fun insert(book: Book): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dao.insert(book) }

    suspend fun update(book: Book) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dao.update(book) }

    suspend fun delete(book: Book) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dao.delete(book) }
}
