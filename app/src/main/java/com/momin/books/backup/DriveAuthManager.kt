package com.momin.books.backup

import android.content.Context

/**
 * Simple token cache for Drive OAuth access tokens.
 * - Stores token and expiry in SharedPreferences.
 * - This is a pragmatic approach for the skeleton; consider EncryptedSharedPreferences or AccountManager for production.
 */
object DriveAuthManager {
    private const val PREFS = "drive_auth"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_EXP = "expires_at"

    fun saveToken(context: Context, token: String, expiresInSec: Long = 3600) {
        val exp = System.currentTimeMillis() + expiresInSec * 1000
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOKEN, token).putLong(KEY_EXP, exp).apply()
    }

    fun getCachedToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        val exp = prefs.getLong(KEY_EXP, 0)
        if (token == null) return null
        if (System.currentTimeMillis() >= exp) {
            clearToken(context)
            return null
        }
        return token
    }

    fun clearToken(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_TOKEN).remove(KEY_EXP).apply()
    }
}
