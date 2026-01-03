package com.momin.books.backup

import org.json.JSONObject

// Reusable DTO used across Drive helpers
data class DriveFileItem(val id: String, val name: String, val createdTime: String?, val size: Long?)

/**
 * Parse a Drive files JSON response into a list of DriveFileItem.
 * This lets us unit-test parsing without network calls.
 */
fun parseDriveFilesJson(body: String): List<DriveFileItem> {
    val results = mutableListOf<DriveFileItem>()
    val jo = JSONObject(body)
    val arr = jo.optJSONArray("files") ?: return results
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val id = o.optString("id")
        val name = o.optString("name")
        val created = if (o.has("createdTime")) o.optString("createdTime", null) else null
        val size = if (o.has("size")) o.optLong("size", 0L) else 0L
        results.add(DriveFileItem(id, name, created, if (size == 0L) null else size))
    }
    return results
}