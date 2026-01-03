package com.momin.books.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.momin.books.data.AppDatabase
import com.momin.books.data.Book
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class BookDetailActivity : ComponentActivity() {
    private var bookId: Int = -1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookId = intent.getIntExtra("bookId", -1)
        setContent {
            var book by remember { mutableStateOf<Book?>(null) }
            LaunchedEffect(bookId) {
                if (bookId >= 0) {
                    val b = AppDatabase.getInstance(applicationContext).bookDao().getById(bookId)
                    book = b
                }
            }

            Scaffold(topBar = { SmallTopAppBar(title = { Text(book?.title ?: "Book") }) }) { padding ->
                Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                    Text("Title: ${book?.title ?: "-"}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Author: ${book?.author ?: "-"}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Genre: ${book?.genre ?: "-"}")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row {
                        Button(onClick = {
                            if (book != null) {
                                val intent = Intent(this@BookDetailActivity, ReaderActivity::class.java).apply {
                                    putExtra("bookId", book!!.id)
                                }
                                startActivity(intent)
                            }
                        }, modifier = Modifier.semantics { contentDescription = "Open ${book?.title ?: "book"} in reader" }) {
                            Text("Open Reader")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            // simple delete
                            lifecycleScope.launch {
                                book?.let {
                                    AppDatabase.getInstance(applicationContext).bookDao().delete(it)
                                    finish()
                                }
                            }
                        }, modifier = Modifier.semantics { contentDescription = "Delete ${book?.title ?: "book"}" }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
