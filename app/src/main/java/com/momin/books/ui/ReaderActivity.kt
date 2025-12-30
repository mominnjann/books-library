package com.momin.books.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.momin.books.data.AppDatabase
import com.momin.books.viewmodel.ReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
class ReaderActivity : ComponentActivity() {
    private var pdfRenderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var currentPageIndex = 0
    private var pageBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bookId = intent.getIntExtra("bookId", -1)
        setContent {
            var book by remember { mutableStateOf<com.momin.books.data.Book?>(null) }
            LaunchedEffect(bookId) {
                if (bookId >= 0) book = AppDatabase.getInstance(applicationContext).bookDao().getById(bookId)
            }

            val vm = ReaderViewModel(application)

            Scaffold(topBar = { SmallTopAppBar(title = { Text(book?.title ?: "Reader") }) }) { padding ->
                Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                    if (book != null) {
                        val path = book!!.filePath
                        if (path.endsWith(".pdf", ignoreCase = true)) {
                            PDFReaderView(path = path, startPage = book!!.lastPage, onPageChanged = { page ->
                                vm.saveProgress(book!!.id, page)
                            })
                        } else if (path.endsWith(".epub", ignoreCase = true)) {
                            EPUBReaderView(path = path)
                        } else {
                            Text("Unsupported format")
                        }
                    } else {
                        Text("Loading...")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            pdfRenderer?.close()
            pfd?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun PDFReaderView(path: String, startPage: Int = 0, onPageChanged: (Int) -> Unit) {
    var pageIndex by remember { mutableStateOf(startPage) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(path, pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val page = renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1))
                val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                bitmap = bmp
                onPageChanged(pageIndex)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            if (bitmap != null) {
                Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "PDF page", modifier = Modifier.fillMaxSize())
            } else {
                Text("Unable to render page")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { if (pageIndex > 0) pageIndex -= 1 }) { Text("Prev") }
            Text("Page ${'$'}{pageIndex + 1}")
            Button(onClick = { pageIndex += 1 }) { Text("Next") }
        }
    }
}

@Composable
fun EPUBReaderView(path: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE

                val sourceFile = File(path)
                try {
                    // Extract EPUB to cache and find first HTML/XHTML to load
                    val outDir = File(ctx.cacheDir, "epub_unzip_${System.currentTimeMillis()}")
                    if (!outDir.exists()) outDir.mkdirs()
                    ZipFile(sourceFile).use { zip ->
                        zip.entries().asSequence().forEach { entry ->
                            val outFile = File(outDir, entry.name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                zip.getInputStream(entry).use { ins ->
                                    BufferedOutputStream(FileOutputStream(outFile)).use { out ->
                                        ins.copyTo(out)
                                    }
                                }
                            }
                        }
                    }

                    val firstHtml = outDir.walkTopDown().firstOrNull { f -> f.isFile && (f.extension.equals("xhtml", true) || f.extension.equals("html", true)) }
                    if (firstHtml != null) {
                        loadUrl("file://${firstHtml.absolutePath}")
                    } else {
                        loadDataWithBaseURL(null, "<html><body><h3>Empty EPUB</h3></body></html>", "text/html", "utf-8", null)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    loadDataWithBaseURL(null, "<html><body><h3>Error loading EPUB</h3></body></html>", "text/html", "utf-8", null)
                }
            }
        }, modifier = Modifier.fillMaxSize())
    }
}
