package com.momin.books.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val author: String,
    val genre: String? = null,
    val filePath: String,   // local path to PDF/EPUB (internal storage)
    val coverUri: String?,  // optional cover image (URI as String)
    val lastPage: Int = 0   // for resume reading
)
