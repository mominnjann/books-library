package com.momin.books.backup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import android.widget.Toast
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
data class BackupItem(val id: String, val name: String, val createdTime: String?, val size: Long?)

@OptIn(ExperimentalMaterial3Api::class)
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
                        val account = acct.account ?: run {
                            showMessage("No account available for sign-in")
                            return@registerForActivityResult
                        }
                        // Launch coroutine to get token and upload
                        val scopeLauncher = lifecycleScope
                        scopeLauncher.launchWhenStarted {
                            val token = withContext(Dispatchers.IO) {
                                try {
                                    GoogleAuthUtil.getToken(this@DriveBackupActivity, account, scope)
                                } catch (e: UserRecoverableAuthException) {
                                    // Need to recover (user consent), launch intent
                                    e.intent?.let { startActivityForResult(it, 1001) }
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
            val scope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }
            var msg by remember { mutableStateOf<String?>(null) }

            // Backups list UI state


            var showListDialog by remember { mutableStateOf(false) }
            var backups by remember { mutableStateOf<List<BackupItem>>(emptyList()) }
            var loadingBackups by remember { mutableStateOf(false) }
            var confirmRestoreFor by remember { mutableStateOf<BackupItem?>(null) }
            var sortMode by remember { mutableStateOf("date_desc") } // date_desc, date_asc, size_desc, size_asc

            fun startSignIn() {
                msg = null
                busy = true
                signInLauncher.launch(signInClient.signInIntent)
            }

            fun formatTime(iso: String?): String {
                if (iso == null || iso.isEmpty()) return "Unknown"
                return try {
                    val t = java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(t)
                } catch (e: Exception) {
                    iso
                }
            }

            fun humanSize(sz: Long?): String {
                if (sz == null || sz <= 0) return "-"
                val kb = sz / 1024.0
                if (kb < 1024) return String.format("%.1f KB", kb)
                val mb = kb / 1024.0
                return String.format("%.1f MB", mb)
            }

            fun fetchAndShowBackups(token: String) {
                scope.launch {
                    loadingBackups = true
                    val list = withContext(Dispatchers.IO) { listBackupsOnDrive(token) }
                    // sort according to mode
                    val sorted = when (sortMode) {
                        "date_asc" -> list.sortedBy { it.createdTime ?: "" }
                        "size_desc" -> list.sortedByDescending { it.size ?: 0L }
                        "size_asc" -> list.sortedBy { it.size ?: Long.MAX_VALUE }
                        else -> list.sortedByDescending { it.createdTime ?: "" } // date_desc
                    }
                    backups = sorted
                    loadingBackups = false
                    showListDialog = true
                }
            }

            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Google Drive Backup", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { startSignIn() }, enabled = !busy) {
                        Text(if (busy) "Signing in..." else "Sign in & Upload Backup")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val cached = DriveAuthManager.getCachedToken(this@DriveBackupActivity)
                        if (cached != null) fetchAndShowBackups(cached)
                        else startSignIn()
                    }, enabled = !busy) {
                        Text("List & Restore from Drive")
                    }
                    Spacer(Modifier.height(8.dp))
                    msg?.let { Text(it) }
                }
            }

            if (showListDialog) {
                AlertDialog(onDismissRequest = { showListDialog = false }) {
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Text("Select a backup to restore", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))

                            // Sort controls
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row {
                                    Text("Sort:", modifier = Modifier.alignByBaseline())
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = { sortMode = "date_desc" }) { Text("Newest") }
                                    Spacer(Modifier.width(4.dp))
                                    Button(onClick = { sortMode = "date_asc" }) { Text("Oldest") }
                                    Spacer(Modifier.width(4.dp))
                                    Button(onClick = { sortMode = "size_desc" }) { Text("Largest") }
                                }
                                Text("${backups.size} found", style = MaterialTheme.typography.bodySmall)
                            }

                            Spacer(Modifier.height(8.dp))

                            if (loadingBackups) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (backups.isEmpty()) {
                                Text("No backups found on Drive.")
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                    items(backups) { item ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.name)
                                                Spacer(Modifier.height(2.dp))
                                                Text("${formatTime(item.createdTime)} • ${humanSize(item.size)}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Button(onClick = { confirmRestoreFor = item }, modifier = Modifier.semantics { contentDescription = "Restore ${item.name}" }) {
                                                Text("Restore")
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showListDialog = false }) { Text("Close") }
                            }
                        }
                    }
                }
            }

            confirmRestoreFor?.let { item ->
                AlertDialog(
                    onDismissRequest = { confirmRestoreFor = null },
                    title = { Text("Confirm Restore") },
                    text = { Text("Restore backup '${item.name}'? This will add books and may overwrite existing ones.") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmRestoreFor = null
                            showListDialog = false
                            scope.launch {
                                // Ensure we have a token
                                val token = DriveAuthManager.getCachedToken(this@DriveBackupActivity)
                                if (token == null) {
                                    showMessage("No valid auth token; please sign in first")
                                    return@launch
                                }
                                // perform restore
                                val out = File(cacheDir, item.name)
                                val ok = downloadFileFromDrive(token, item.id, out)
                                if (!ok) {
                                    showMessage("Failed to download backup")
                                    return@launch
                                }
                                val restored = com.momin.books.backup.BackupManager.restoreLibrary(this@DriveBackupActivity, out)
                                if (restored) showMessage("Restore complete") else showMessage("Restore failed")
                            }
                        }) { Text("Restore") }
                    },
                    dismissButton = { TextButton(onClick = { confirmRestoreFor = null }) { Text("Cancel") } }
                )
            }
        }
    }

    private fun showMessage(text: String) {
        // Simple toast + finish with message in intent result
        runOnUiThread {
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
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

    private fun startRestoreFlow() {
        // Sign-in if necessary, or use cached token
        val cached = DriveAuthManager.getCachedToken(this)
        if (cached != null) {
            lifecycleScope.launchWhenStarted {
                restoreLatestBackup(cached)
            }
        } else {
            // launch sign-in flow; after sign-in the existing code will cache token and we can call restore
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
                .build()
            val signInClient = GoogleSignIn.getClient(this, gso)
            val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val acct = task.getResult(Exception::class.java)
                        if (acct != null) {
                            lifecycleScope.launchWhenStarted {
                                val scope = "oauth2:https://www.googleapis.com/auth/drive.file"
                                val token = withContext(Dispatchers.IO) {
                                    try {
                                        val account = acct.account ?: return@withContext null
                                        GoogleAuthUtil.getToken(this@DriveBackupActivity, account, scope)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        null
                                    }
                                }
                                if (token != null) {
                                    DriveAuthManager.saveToken(this@DriveBackupActivity, token, 3600)
                                    restoreLatestBackup(token)
                                } else {
                                    showMessage("Unable to obtain token for restore")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showMessage("Sign-in failed")
                    }
                } else {
                    showMessage("Sign-in canceled")
                }
            }
            signInLauncher.launch(signInClient.signInIntent)
        }
    }

    private suspend fun restoreLatestBackup(token: String) {
        withContext(Dispatchers.IO) {
            try {
                val files = listBackupsOnDrive(token)
                if (files.isEmpty()) {
                    showMessage("No backups found on Drive")
                    return@withContext
                }
                val latest = files.first()
                val out = File(cacheDir, latest.name)
                val ok = downloadFileFromDrive(token, latest.id, out)
                if (!ok) {
                    showMessage("Failed to download backup")
                    return@withContext
                }

                val restored = com.momin.books.backup.BackupManager.restoreLibrary(this@DriveBackupActivity, out)
                if (restored) {
                    showMessage("Restore complete")
                } else {
                    showMessage("Restore failed: invalid backup")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showMessage("Error during restore: ${e.message}")
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

    private fun listBackupsOnDrive(token: String): List<BackupItem> {
        // Return list of BackupItem (id, name, createdTime, size)
        val q = "name contains 'books_export' and mimeType='application/zip'"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&orderBy=createdTime%20desc&fields=files(id,name,createdTime,size)"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val results = mutableListOf<BackupItem>()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 401) DriveAuthManager.clearToken(this@DriveBackupActivity)
                return results
            }
            val body = resp.body?.string() ?: return results
            val jo = org.json.JSONObject(body)
            val arr = jo.optJSONArray("files") ?: return results
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                val name = o.optString("name")
                val created = if (o.has("createdTime")) o.optString("createdTime", null) else null
                val size = if (o.has("size")) o.optLong("size", 0L) else 0L
                results.add(BackupItem(id, name, created, if (size == 0L) null else size))
            }
        }
        return results
    }

    private fun downloadFileFromDrive(token: String, fileId: String, dest: File): Boolean {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 401) DriveAuthManager.clearToken(this@DriveBackupActivity)
                return false
            }
            resp.body?.byteStream()?.use { ins ->
                dest.outputStream().use { outs ->
                    ins.copyTo(outs)
                }
            }
            return true
        }
    }
}
