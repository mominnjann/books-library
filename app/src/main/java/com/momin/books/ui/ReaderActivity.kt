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
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // Reader state
    var outDir by remember { mutableStateOf<File?>(null) }
    var toc by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // (href, title)
    var currentHref by remember { mutableStateOf<String?>(null) }
    var showToc by remember { mutableStateOf(false) }
    var fontScale by remember { mutableStateOf(100) }
    var darkMode by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            try {
                val sourceFile = File(path)
                val out = File(context.cacheDir, "epub_unzip_${System.currentTimeMillis()}")
                if (!out.exists()) out.mkdirs()
                ZipFile(sourceFile).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        val outFile = File(out, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { ins ->
                                BufferedOutputStream(FileOutputStream(outFile)).use { outStream ->
                                    ins.copyTo(outStream)
                                }
                            }
                        }
                    }
                }

                // Find toc by searching for nav or toc files
                val htmlFiles = out.walkTopDown().filter { it.isFile && (it.extension.equals("xhtml", true) || it.extension.equals("html", true)) }.toList()

                // Look for nav element inside files
                val tocEntries = mutableListOf<Pair<String, String>>()
                for (f in htmlFiles) {
                    val text = f.readText()
                    if (text.contains("<nav", ignoreCase = true) && text.contains("toc", ignoreCase = true)) {
                        // simple regex to capture links inside nav
                        val re = Regex("<a[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL)
                        re.findAll(text).forEach { m ->
                            val href = m.groupValues[1]
                            val title = m.groupValues[2].replace(Regex("<.*?>"), "").trim()
                            tocEntries.add(href to title)
                        }
                    }
                }

                if (tocEntries.isEmpty()) {
                    // fallback: list html files
                    val ordered = htmlFiles.sortedBy { it.absolutePath }
                    ordered.forEach { f -> tocEntries.add(f.relativeTo(out).path to f.name) }
                }

                outDir = out
                toc = tocEntries
                if (tocEntries.isNotEmpty()) {
                    // make first chapter default
                    currentHref = tocEntries[0].first
                } else if (htmlFiles.isNotEmpty()) {
                    currentHref = htmlFiles[0].relativeTo(out).path
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Controls
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                Button(onClick = { showToc = true }) { Text("TOC") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { fontScale = (fontScale - 10).coerceAtLeast(50) }) { Text("A-") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { fontScale = (fontScale + 10).coerceAtMost(200) }) { Text("A+") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { darkMode = !darkMode }) { Text(if (darkMode) "Light" else "Dark") }
            }
        }

        // WebView
        AndroidView(factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true // allow JS for injecting CSS dynamically
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // apply font scale
                        settings.textZoom = fontScale
                        // apply theme
                        if (darkMode) {
                            evaluateJavascript("(function(){document.documentElement.style.backgroundColor='#111';document.documentElement.style.color='#eee';document.body.style.background='#111';document.body.style.color='#eee';})()", null)
                        } else {
                            evaluateJavascript("(function(){document.documentElement.style.backgroundColor='';document.documentElement.style.color='';document.body.style.background='';document.body.style.color='';})()", null)
                        }
                    }
                }

                // initial load after outDir and currentHref are prepared
                LaunchedEffect(outDir, currentHref) {
                    if (outDir != null && currentHref != null) {
                        val f = File(outDir, currentHref!!)
                        if (f.exists()) loadUrl("file://${f.absolutePath}")
                    }
                }
            }
        }, modifier = Modifier.weight(1f))

        // Simple chapter navigation
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                val idx = toc.indexOfFirst { it.first == currentHref }
                if (idx > 0) currentHref = toc[idx - 1].first
            }) { Text("Prev") }
            Text(text = toc.indexOfFirst { it.first == currentHref }.let { if (it >= 0) "Chapter ${it + 1}/${toc.size}" else "-" })
            Button(onClick = {
                val idx = toc.indexOfFirst { it.first == currentHref }
                if (idx >= 0 && idx < toc.size - 1) currentHref = toc[idx + 1].first
            }) { Text("Next") }
        }

        // TOC dialog
        if (showToc) {
            AlertDialog(onDismissRequest = { showToc = false }) {
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("Table of Contents", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(toc) { item ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.second)
                                        Text(item.first, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Button(onClick = { currentHref = item.first; showToc = false }) { Text("Open") }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showToc = false }) { Text("Close") }
                        }
                    }
                }
            }
        }
    }
}
