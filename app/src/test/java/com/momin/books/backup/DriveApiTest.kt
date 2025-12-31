package com.momin.books.backup

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class DriveApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    @Test
    fun listBackups_parsesResponse() {
        val json = """
        { "files": [ {"id":"1","name":"books_export_1.zip","createdTime":"2024-01-02T12:00:00.000Z","size":"2048"} ] }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val api = DriveApi(client, server.url("").toString(), onUnauthorized = null)
        val items = api.listBackups("token")
        assertEquals(1, items.size)
        val it = items[0]
        assertEquals("1", it.id)
        assertEquals("books_export_1.zip", it.name)
        assertEquals("2024-01-02T12:00:00.000Z", it.createdTime)
        assertEquals(2048L, it.size)
    }

    @Test
    fun downloadFile_savesBytes() {
        val data = "ZIPDATA".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(data)))

        val api = DriveApi(client, server.url("").toString(), onUnauthorized = null)
        val dest = File.createTempFile("test", "zip")
        dest.deleteOnExit()
        val ok = api.downloadFile("t", "1", dest)
        assertTrue(ok)
        val read = dest.readBytes()
        assertArrayEquals(data, read)
    }

    @Test
    fun unauthorized_callsCallback() {
        server.enqueue(MockResponse().setResponseCode(401))
        var cleared = false
        val api = DriveApi(client, server.url("").toString(), onUnauthorized = { cleared = true })
        val list = api.listBackups("t")
        assertTrue(list.isEmpty())
        assertTrue(cleared)
    }
}