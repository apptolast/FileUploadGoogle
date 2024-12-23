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
import java.io.FileInputStream
import java.io.InputStreamReader

@Service(Service.Level.PROJECT)
class GoogleDriveService(private val project: Project) {
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val scopes = listOf(DriveScopes.DRIVE_FILE)
    private val credentialsPath = "/client_secret.json" // Asegúrate de tener este archivo en resources
    private val tokensDirectoryPath = "tokens"

    private fun getCredentials(): Credential {
        // Cargar cliente secreto desde archivo
        val inputStream = GoogleDriveService::class.java.getResourceAsStream(credentialsPath)
            ?: throw Exception("Resource not found: $credentialsPath")

        val clientSecrets = GoogleClientSecrets.load(jsonFactory, InputStreamReader(inputStream))

        // Construir flow y solicitar un token
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

    fun uploadFile(filePath: String, parentFolderId: String? = null): String {
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

    fun uploadDirectory(directoryPath: String): String {
        val directory = File(directoryPath)
        val folderName = directory.name

        // Crear carpeta principal
        val mainFolderId = createFolder(folderName)

        // Subir archivos recursivamente
        uploadDirectoryContents(directory, mainFolderId)

        return mainFolderId
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