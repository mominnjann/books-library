package com.momin.books.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityTests {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coreElements_haveContentDescriptions() {
        // FAB Add Book
        rule.onNodeWithContentDescription("Add Book").assertExists()
        // Share / Export icon
        rule.onNodeWithContentDescription("Export").assertExists()
    }
}