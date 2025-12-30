package com.momin.books.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.momin.books.viewmodel.AddBookViewModel

@OptIn(ExperimentalMaterial3Api::class)
class AddBookActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriString = intent.getStringExtra("uri")
        val uri = uriString?.let { Uri.parse(it) }
        setContent {
            val vm: AddBookViewModel = viewModel()
            var title by remember { mutableStateOf("") }
            var author by remember { mutableStateOf("") }
            var genre by remember { mutableStateOf("") }
            var isSaving by remember { mutableStateOf(false) }

            Scaffold(topBar = { SmallTopAppBar(title = { Text("Add Book") }) }) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Author") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Genre") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        if (uri == null) {
                            Toast.makeText(this@AddBookActivity, "No file selected", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (title.isBlank() || author.isBlank()) {
                            Toast.makeText(this@AddBookActivity, "Enter title and author", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // Ensure we have persistent read access
                        try {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: Exception) { e.printStackTrace() }

                        isSaving = true
                        vm.addBookFromUri(uri, title, author, if (genre.isBlank()) null else genre) { id ->
                            runOnUiThread {
                                isSaving = false
                                if (id > 0) {
                                    Toast.makeText(this@AddBookActivity, "Book added", Toast.LENGTH_SHORT).show()
                                    finish()
                                } else {
                                    Toast.makeText(this@AddBookActivity, "Failed to add", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving) {
                        if (isSaving) Text("Saving...") else Text("Save")
                    }
                }
            }
        }
    }
}
