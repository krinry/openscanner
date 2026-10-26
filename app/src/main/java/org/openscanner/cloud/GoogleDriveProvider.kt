package org.openscanner.cloud

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

/**
 * Google Drive synchronization provider using Google Play Services Auth and Google Drive REST API v3.
 */
class GoogleDriveProvider(private val context: Context) : CloudProvider {

    override val id: String = "gdrive"
    override val displayName: String = "Google Drive"

    private val prefs = context.getSharedPreferences("openscanner_cloud_sync", Context.MODE_PRIVATE)

    private val signInOptions: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE_APPDATA))
            .build()
    }

    private val googleSignInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, signInOptions)
    }

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    suspend fun onAccountSignedIn(account: GoogleSignInAccount) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString("gdrive_account_email", account.email ?: "Google Drive User")
            .putBoolean("sync_gdrive", true)
            .apply()
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val hasScope = account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
        val prefEnabled = prefs.getBoolean("sync_gdrive", false)
        prefEnabled && ((account != null && hasScope) || prefs.getString("gdrive_account_email", null) != null)
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            googleSignInClient.signOut()
        } catch (e: Exception) {
            // Ignore sign out error
        }
        prefs.edit()
            .remove("gdrive_account_email")
            .putBoolean("sync_gdrive", false)
            .apply()
    }

    override suspend fun getAccountIdentifier(): String = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        account?.email ?: prefs.getString("gdrive_account_email", "Google Drive") ?: "Google Drive"
    }

    override suspend fun getQuota(): CloudQuota? = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService() ?: return@withContext null
            val about = drive.about().get().setFields("storageQuota").execute()
            val quota = about.storageQuota ?: return@withContext null
            val used = quota.usage ?: 0L
            val limit = quota.limit ?: (15L * 1024 * 1024 * 1024) // Fallback 15GB standard free tier
            CloudQuota(usedBytes = used, totalBytes = limit)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun uploadFile(file: File, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService()
                ?: return@withContext Result.failure(IllegalStateException("Google Drive is not authenticated"))

            val folderId = getOrCreateOpenScannerFolder(drive)
            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = file.name
                parents = Collections.singletonList(folderId)
            }
            val mediaContent = FileContent(mimeType, file)
            val uploaded = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, webViewLink")
                .execute()

            Result.success(uploaded.id ?: file.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncScans(files: List<File>): SyncResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext SyncResult.Error("Google Drive is not connected")
        }
        val drive = getDriveService() ?: return@withContext SyncResult.Error("Google Drive service unavailable")

        var uploadedCount = 0
        try {
            val folderId = getOrCreateOpenScannerFolder(drive)
            for (file in files) {
                if (!file.exists() || file.length() == 0L) continue
                val mime = if (file.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg"
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = file.name
                    parents = Collections.singletonList(folderId)
                }
                val mediaContent = FileContent(mime, file)
                drive.files().create(fileMetadata, mediaContent).setFields("id").execute()
                uploadedCount++
            }
            SyncResult.Success(uploadedCount, "Successfully uploaded $uploadedCount files to Google Drive/OpenScanner")
        } catch (e: Exception) {
            SyncResult.Error("Google Drive sync failed: ${e.localizedMessage ?: "Unknown error"}", e)
        }
    }

    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA)
        ).apply {
            selectedAccount = account.account
        }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("OpenScanner").build()
    }

    private fun getOrCreateOpenScannerFolder(drive: Drive): String {
        val query = "mimeType='application/vnd.google-apps.folder' and name='OpenScanner' and trashed=false"
        val result = drive.files().list().setQ(query).setSpaces("drive").setFields("files(id, name)").execute()
        val existing = result.files?.firstOrNull()
        if (existing != null) {
            return existing.id
        }

        val folderMetadata = com.google.api.services.drive.model.File().apply {
            name = "OpenScanner"
            mimeType = "application/vnd.google-apps.folder"
        }
        val folder = drive.files().create(folderMetadata).setFields("id").execute()
        return folder.id
    }
}
