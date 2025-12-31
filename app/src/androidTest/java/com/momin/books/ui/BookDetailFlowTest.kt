package com.momin.books.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.momin.books.data.AppDatabase
import com.momin.books.data.Book
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDetailFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun deleteBook_removesFromList() {
        val context = rule.activity.applicationContext
        val book = Book(title = "ToDelete", author = "Del", genre = "", filePath = "", coverUri = null)
        runBlocking { AppDatabase.getInstance(context).bookDao().insert(book) }

        rule.waitForIdle()

        // open book from list
        rule.onNodeWithContentDescription("Open ToDelete by Del").performClick()

        // click delete
        rule.onNodeWithContentDescription("Delete ToDelete").performClick()

        // assert book card no longer exists
        rule.waitUntil(3000) {
            try {
                rule.onNodeWithContentDescription("Open ToDelete by Del").assertDoesNotExist()
                true
            } catch (e: AssertionError) { false }
        }
    }
}