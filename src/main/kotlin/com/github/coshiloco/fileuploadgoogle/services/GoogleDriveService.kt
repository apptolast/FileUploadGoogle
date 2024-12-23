package com.github.coshiloco.fileuploadgoogle.services

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.io.InputStreamReader

@Service(Service.Level.PROJECT)
class GoogleDriveService(private val project: Project) {
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val scopes = listOf(DriveScopes.DRIVE_FILE)
    private val credentialsPath = "resources/client_secret_133280984050-657tfstbbfskvrihs3umtt1ed82d9ubv.apps.googleusercontent.com.json"
    private val tokensDirectoryPath = "tokens"

    private fun getCredentials(): Credential {
        val inputStream = GoogleDriveService::class.java.getResourceAsStream(credentialsPath)
            ?: throw Exception("Resource not found: $credentialsPath")

        val clientSecrets = GoogleClientSecrets.load(jsonFactory, InputStreamReader(inputStream))

        val flow = GoogleAuthorizationCodeFlow.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            jsonFactory,
            clientSecrets,
            scopes
        ).setDataStoreFactory(FileDataStoreFactory(File(tokensDirectoryPath)))
            .setAccessType("offline")
            .build()

        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    private fun getDriveService(): Drive {
        return Drive.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            jsonFactory,
            getCredentials()
        ).setApplicationName("IntelliJ Plugin Backup").build()
    }

    fun findFolderByPath(path: List<String>): String? {
        var currentParentId = "root"

        for (folderName in path) {
            currentParentId = findFolder(folderName, currentParentId) ?: return null
        }

        return currentParentId
    }

    private fun findFolder(folderName: String, parentId: String): String? {
        val query = "'${parentId}' in parents and " +
                "mimeType='application/vnd.google-apps.folder' and " +
                "name='${folderName}' and " +
                "trashed=false"

        val result = getDriveService().files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        return result.files.firstOrNull()?.id
    }

    private fun createFolderPath(path: List<String>): String {
        var currentParentId = "root"

        for (folderName in path) {
            currentParentId = findFolder(folderName, currentParentId)
                ?: createFolder(folderName, currentParentId)
        }

        return currentParentId
    }

    fun uploadToSpecificFolder(directoryPath: String) {
        val targetPath = listOf("AppToLast", "TestPlugin")
        val targetFolderId = findFolderByPath(targetPath) ?: createFolderPath(targetPath)
        val directory = File(directoryPath)
        uploadDirectoryContents(directory, targetFolderId)
    }

    fun createFolder(folderName: String, parentFolderId: String? = null): String {
        val fileMetadata = com.google.api.services.drive.model.File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
            if (parentFolderId != null) {
                parents = listOf(parentFolderId)
            }
        }

        val folder = getDriveService().files().create(fileMetadata)
            .setFields("id")
            .execute()

        return folder.id
    }

    private fun uploadFile(filePath: String, parentFolderId: String? = null): String {
        val file = File(filePath)
        val fileMetadata = com.google.api.services.drive.model.File().apply {
            name = file.name
            if (parentFolderId != null) {
                parents = listOf(parentFolderId)
            }
        }

        val mediaContent = FileContent("application/octet-stream", file)
        val uploadedFile = getDriveService().files().create(fileMetadata, mediaContent)
            .setFields("id, name, webViewLink")
            .execute()

        return uploadedFile.webViewLink
    }

    private fun uploadDirectoryContents(directory: File, parentFolderId: String) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val newFolderId = createFolder(file.name, parentFolderId)
                uploadDirectoryContents(file, newFolderId)
            } else {
                uploadFile(file.absolutePath, parentFolderId)
            }
        }
    }
}