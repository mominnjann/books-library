package com.momin.books.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.momin.books.data.AppDatabase
import com.momin.books.data.Book
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear DB
        val db = AppDatabase.getInstance(context)
        db.clearAllTables()
    }

    @After
    fun tearDown() {
        val db = AppDatabase.getInstance(context)
        db.close()
    }

    @Test
    fun restoreLibrary_restoresFilesAndInsertsBooks() = runBlocking {
        val cache = context.cacheDir
        val tmpDir = File(cache, "test_restore")
        tmpDir.mkdirs()

        // Create dummy book file and cover
        val bookFile = File(tmpDir, "dummy.pdf")
        bookFile.writeText("PDFDATA")
        val coverFile = File(tmpDir, "cover.jpg")
        coverFile.writeText("COVER")

        // metadata.json
        val meta = "[ { \"id\": 1, \"title\": \"Test Book\", \"author\": \"Author X\", \"genre\": \"Fiction\", \"filePath\": \"dummy.pdf\", \"coverUri\": \"cover.jpg\", \"lastPage\": 5 } ]"
        val metaFile = File(tmpDir, "metadata.json")
        metaFile.writeText(meta)

        // zip it
        val zipFile = File(cache, "test_books_export.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            tmpDir.listFiles()?.forEach { f ->
                val entry = ZipEntry(f.name)
                zos.putNextEntry(entry)
                f.inputStream().copyTo(zos)
                zos.closeEntry()
            }
        }

        val ok = BackupManager.restoreLibrary(context, zipFile)
        assertTrue(ok)

        val dao = AppDatabase.getInstance(context).bookDao()
        val booksFlow = dao.getAll()
        val books = kotlinx.coroutines.flow.first(booksFlow)
        assertEquals(1, books.size)
        val b = books[0]
        assertEquals("Test Book", b.title)
        assertEquals("Author X", b.author)
        assertEquals("Fiction", b.genre)
        assertTrue(File(b.filePath).exists())
        assertNotNull(b.coverUri)
        assertEquals(5, b.lastPage)

        // cleanup
        tmpDir.deleteRecursively()
        zipFile.delete()
    }
}