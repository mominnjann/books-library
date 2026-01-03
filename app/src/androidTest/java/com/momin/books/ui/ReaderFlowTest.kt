package com.momin.books.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.momin.books.data.AppDatabase
import com.momin.books.data.Book
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ReaderFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun createTestEpub(contextFilesDir: File): File {
        val epub = File(contextFilesDir, "test_epub.epub")
        ZipOutputStream(epub.outputStream()).use { zos ->
            val index = "<html><body><nav class=\"toc\"><ul><li><a href=\"chapter1.html\">Chapter 1</a></li><li><a href=\"chapter2.html\">Chapter 2</a></li></ul></nav><h1>Index</h1></body></html>"
            val c1 = "<html><body><h1>Chapter 1</h1><p>Content 1</p></body></html>"
            val c2 = "<html><body><h1>Chapter 2</h1><p>Content 2</p></body></html>"
            zos.putNextEntry(ZipEntry("index.html")); zos.write(index.toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("chapter1.html")); zos.write(c1.toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("chapter2.html")); zos.write(c2.toByteArray()); zos.closeEntry()
        }
        return epub
    }

    @Test
    fun epubToc_and_chapterNavigation_works() {
        val context = rule.activity.applicationContext
        val epub = createTestEpub(context.filesDir)

        // insert book
        val book = Book(title = "EPUB Test", author = "Author", genre = "", filePath = epub.absolutePath, coverUri = null)
        runBlocking { AppDatabase.getInstance(context).bookDao().insert(book) }

        rule.waitForIdle()

        // open book from list
        rule.onNodeWithContentDescription("Open EPUB Test by Author").performClick()
        // open reader
        rule.onNodeWithContentDescription("Open EPUB Test in reader").performClick()

        // wait for reader to initialize and show TOC button
        rule.waitUntil(5000) {
            try {
                rule.onNodeWithContentDescription("Open table of contents").assertExists()
                true
            } catch (e: AssertionError) { false }
        }

        // open TOC
        rule.onNodeWithContentDescription("Open table of contents").performClick()
        // check TOC dialog visible
        rule.onNodeWithText("Table of Contents").assertExists()

        // open chapter 2
        rule.onNodeWithContentDescription("Open Chapter 2").performClick()

        // chapter indicator should show Chapter 2/2
        rule.onNodeWithText("Chapter 2/2").assertExists()
    }
}