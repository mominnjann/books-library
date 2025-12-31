package com.momin.books.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.momin.books.data.Book
import com.momin.books.viewmodel.BookListViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: BookListViewModel = viewModel()
            val books by vm.books.collectAsState()
            val context = LocalContext.current

            Scaffold(
                topBar = {
                    SmallTopAppBar(title = { Text("Library") }, actions = {
                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()
                        IconButton(onClick = {
                            scope.launch {
                                val out = File(context.cacheDir, "books_export_${System.currentTimeMillis()}.zip")
                                val ok = com.momin.books.backup.BackupManager.exportLibrary(context, out)
                                if (ok) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", out)
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Export library"))
                                }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Export")
                        }

                        // Drive backup
                        IconButton(onClick = {
                            context.startActivity(Intent(context, com.momin.books.backup.DriveBackupActivity::class.java))
                        }) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Backup to Drive")
                        }
                    })
                },
                floatingActionButton = {
                    val pickLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                        onResult = { uri: Uri? ->
                            if (uri != null) {
                                // Persist access to the document so the app can read it later
                                try {
                                    context.contentResolver.takePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                // Launch AddBookActivity with selected URI
                                val intent = Intent(context, AddBookActivity::class.java).apply {
                                    putExtra("uri", uri.toString())
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                    FloatingActionButton(onClick = {
                        pickLauncher.launch(arrayOf("application/pdf", "application/epub+zip", "application/octet-stream"))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Book")
                    }
                }
            ) { padding ->
                BookList(books = books, onClick = { book ->
                    val intent = Intent(context, BookDetailActivity::class.java).apply { putExtra("bookId", book.id) }
                    context.startActivity(intent)
                }, modifier = Modifier.padding(padding))
            }
        }
    }
}

@Composable
fun BookList(books: List<Book>, onClick: (Book) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(8.dp)) {
        items(books) { book ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick(book) }) {
                Row(modifier = Modifier.padding(12.dp)) {
                    val img = remember(book.coverUri) {
                        book.coverUri?.let { path ->
                            val f = File(path)
                            if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                        }
                    }
                    if (img != null) {
                        Image(bitmap = img.asImageBitmap(), contentDescription = "cover", modifier = Modifier.size(72.dp))
                    } else {
                        Box(modifier = Modifier.size(72.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(book.title, style = MaterialTheme.typography.titleMedium)
                        Text(book.author, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
