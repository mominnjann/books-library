package com.momin.books.backup

import org.junit.Assert.*
import org.junit.Test

class DriveApiUtilsTest {
    @Test
    fun parseDriveFilesJson_parsesValues() {
        val json = """
        {
          "files": [
            {"id": "1", "name": "books_export_1.zip", "createdTime": "2024-01-02T12:34:56.000Z", "size": "2048"},
            {"id": "2", "name": "other.zip", "createdTime": "2024-01-01T10:00:00.000Z", "size": "1024"}
          ]
        }
        """.trimIndent()

        val items = parseDriveFilesJson(json)
        assertEquals(2, items.size)
        assertEquals("1", items[0].id)
        assertEquals("books_export_1.zip", items[0].name)
        assertEquals("2024-01-02T12:34:56.000Z", items[0].createdTime)
        assertEquals(2048L, items[0].size)

        assertEquals("2", items[1].id)
        assertEquals("other.zip", items[1].name)
        assertEquals("2024-01-01T10:00:00.000Z", items[1].createdTime)
        assertEquals(1024L, items[1].size)
    }
}