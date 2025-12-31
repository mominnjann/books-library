package com.momin.books.backup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File

/**
 * Simple Activity that handles Google Sign-In for Drive scopes and uploads the exported ZIP.
 * This is a minimal skeleton to demonstrate auth and upload; handle errors & token persistence for production.
 */
class DriveBackupActivity : ComponentActivity() {
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()

        val signInClient = GoogleSignIn.getClient(this, gso)

        // If we already have a cached token that hasn't expired, attempt upload immediately
        val cached = DriveAuthManager.getCachedToken(this)
        if (cached != null) {
            lifecycleScope.launchWhenStarted {
                uploadBackupWithToken(cached)
            }
        }

        val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val acct = task.getResult(Exception::class.java)
                    if (acct != null) {
                        // Acquire OAuth2 access token on background thread
                        val scope = "oauth2:https://www.googleapis.com/auth/drive.file"
                        val account = acct.account
                        // Launch coroutine to get token and upload
                        val scopeLauncher = lifecycleScope
                        scopeLauncher.launchWhenStarted {
                            val token = withContext(Dispatchers.IO) {
                                try {
                                    GoogleAuthUtil.getToken(this@DriveBackupActivity, account, scope)
                                } catch (e: UserRecoverableAuthException) {
                                    // Need to recover (user consent), launch intent
                                    startActivityForResult(e.intent, 1001)
                                    null
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    null
                                }
                            }

                            if (token != null) {
                                // Cache token with a 1-hour lifetime (typical access token duration)
                                DriveAuthManager.saveToken(this@DriveBackupActivity, token, 3600)
                                uploadBackupWithToken(token)
                            } else {
                                showMessage("Unable to obtain auth token")
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    showMessage("Sign-in failed: ${e.message}")
                }
            } else {
                showMessage("Sign-in canceled")
            }
        }

        setContent {
            var busy by remember { mutableStateOf(false) }
            var msg by remember { mutableStateOf<String?>(null) }

            fun startSignIn() {
                msg = null
                busy = true
                signInLauncher.launch(signInClient.signInIntent)
            }

            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Google Drive Backup", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { startSignIn() }, enabled = !busy) {
                        Text(if (busy) "Signing in..." else "Sign in & Upload Backup")
                    }
                    Spacer(Modifier.height(8.dp))
                    msg?.let { Text(it) }
                }
            }
        }
    }

    private fun showMessage(text: String) {
        // Simple toast replacement: use log + finish with message in intent result
        runOnUiThread {
            val i = Intent().apply { putExtra("message", text) }
            setResult(Activity.RESULT_OK, i)
        }
    }

    private fun uploadBackupWithToken(token: String) {
        lifecycleScope.launchWhenStarted {
            try {
                // Create temporary ZIP
                val out = File(cacheDir, "books_export_${System.currentTimeMillis()}.zip")
                val ok = com.momin.books.backup.BackupManager.exportLibrary(this@DriveBackupActivity, out)
                if (!ok) {
                    showMessage("Failed to create backup ZIP")
                    return@launchWhenStarted
                }

                val success = withContext(Dispatchers.IO) {
                    uploadFileToDrive(token, out)
                }

                if (success) {
                    showMessage("Backup uploaded to Drive successfully")
                } else {
                    showMessage("Upload failed")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showMessage("Error: ${e.message}")
            }
        }
    }

    private fun uploadFileToDrive(token: String, file: File): Boolean {
        // Multipart upload per Drive API (metadata part + file part)
        val metadata = "{" + "\"name\": \"${file.name}\"" + "}"

        val metadataPart = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), metadata)
        val fileBody = RequestBody.create("application/zip".toMediaTypeOrNull(), file)

        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("metadata", null, metadataPart)
            .addFormDataPart("file", file.name, fileBody)
            .build()

        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .addHeader("Authorization", "Bearer $token")
            .post(multipart)
            .build()

        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) return true
            // Clear cached token on 401 so next run forces re-auth
            if (resp.code == 401) {
                DriveAuthManager.clearToken(this@DriveBackupActivity)
            }
            return false
        }
    }
}
