package com.github.coshiloco.fileuploadgoogle.actions

import com.github.coshiloco.fileuploadgoogle.services.ProjectBackupService
import com.github.coshiloco.fileuploadgoogle.services.GoogleDriveService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class BackupAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            Messages.showErrorDialog("No project found", "Error")
            return
        }

        val basePath = project.basePath
        if (basePath == null) {
            Messages.showErrorDialog("No project path found", "Error")
            return
        }

        // Obtener el servicio de backup
        val backupService = project.getService(ProjectBackupService::class.java)

        // Mostrar diálogo de opciones
        val options = arrayOf("Iniciar monitoreo automático", "Detener monitoreo", "Ejecutar backup ahora")
        val choice = Messages.showChooseDialog(
            project,
            "¿Qué acción desea realizar?",
            "Backup Automático",
            null,
            options,
            options[0]
        )

        when (choice) {
            0 -> {
                backupService.startMonitoring()
                Messages.showInfoMessage(
                    "El monitoreo automático se ha iniciado.\nSe realizarán copias cada 5 minutos y cuando haya cambios significativos.",
                    "Monitoreo Iniciado"
                )
            }

            1 -> {
                backupService.stopMonitoring()
                Messages.showInfoMessage(
                    "El monitoreo automático se ha detenido.",
                    "Monitoreo Detenido"
                )
            }

            2 -> executeBackup(project, basePath)
        }


    }

    fun executeBackup(project: Project, basePath: String) {
        val projectDetails = StringBuilder()
        projectDetails.append("=== ANÁLISIS DETALLADO DEL PROYECTO ===\n")
        projectDetails.append("Fecha y hora del análisis: ${getCurrentDateTime()}\n")
        projectDetails.append("Ruta base: $basePath\n\n")

        val stats = ScanStats()

        // Escanear estructura física del proyecto
        projectDetails.append("=== ESTRUCTURA FÍSICA ===\n")
        scanDirectoryDetailed(File(basePath), projectDetails, stats = stats)

        // Escanear estructura lógica del proyecto
        projectDetails.append("\n=== ESTRUCTURA LÓGICA ===\n")
        scanProjectStructure(project, projectDetails)

        // Añadir estadísticas al final
        projectDetails.append("\n=== ESTADÍSTICAS ===\n")
        projectDetails.append("Total archivos: ${stats.filesCount}\n")
        projectDetails.append("Total directorios: ${stats.directoriesCount}\n")
        projectDetails.append("Tamaño total: ${formatFileSize(stats.totalSize)}\n")

        // Guardar el reporte en un archivo
        saveReport(basePath, projectDetails.toString())

        // Después de generar el reporte, hacer la copia completa
        val projectName = File(basePath).name
        val backupFolderName = "${projectName}_copia"
        val backupPath = File(basePath, backupFolderName)

        try {
            // Si existe la carpeta de copia, la eliminamos
            if (backupPath.exists()) {
                backupPath.deleteRecursively()
            }

            // Crear la carpeta de copia
            backupPath.mkdirs()

            // Realizar la copia
            projectDetails.append("\n=== PROCESO DE COPIA ===\n")
            copyProjectStructure(File(basePath), backupPath, projectDetails)

            // Actualizar el mensaje del diálogo
            showSummaryDialog(project, stats, backupPath.absolutePath)

            val driveService = project.getService(GoogleDriveService::class.java)

            // Ahora usamos el nuevo método que sube directamente a la carpeta específica
            driveService.uploadToSpecificFolder(backupPath.absolutePath)

            Messages.showInfoMessage(
                "El backup se ha subido correctamente a la carpeta TestPlugin en Google Drive",
                "Subida Completada"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Error al crear la copia: ${e.message}",
                "Error en la Copia"
            )
        }
    }

    private data class ScanStats(
        var filesCount: Int = 0,
        var directoriesCount: Int = 0,
        var totalSize: Long = 0
    )

    private fun copyProjectStructure(sourceDir: File, targetDir: File, log: StringBuilder) {
        try {
            // Evitar copiar dentro de la carpeta de copia
            if (sourceDir.absolutePath.contains("_copia")) {
                return
            }

            // Crear el directorio destino si no existe
            if (!targetDir.exists()) {
                targetDir.mkdirs()
                log.append("Creado directorio: ${targetDir.absolutePath}\n")
            }

            sourceDir.listFiles()?.forEach { source ->
                // Evitar copiar archivos del reporte y la carpeta de copia
                if (!source.name.contains("project_scan_") &&
                    !source.name.endsWith("_copia") &&
                    !source.name.equals("build") &&
                    !source.name.equals("out")
                ) {

                    val target = File(targetDir, source.name)

                    if (source.isDirectory) {
                        copyProjectStructure(source, target, log)
                    } else {
                        try {
                            source.copyTo(target, overwrite = true)
                            log.append("Copiado: ${source.absolutePath} -> ${target.absolutePath}\n")
                        } catch (e: Exception) {
                            log.append("Error copiando ${source.absolutePath}: ${e.message}\n")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.append("Error en la copia: ${e.message}\n")
        }
    }

    private fun scanDirectoryDetailed(directory: File, output: StringBuilder, indent: String = "", stats: ScanStats) {
        try {
            directory.listFiles()?.forEach { file ->
                val path = file.toPath()
                val size = try {
                    Files.size(path)
                } catch (e: Exception) {
                    -1L
                }

                if (file.isDirectory) {
                    stats.directoriesCount++
                    output.append("\n$indent📁 ${file.name}/\n")
                    scanDirectoryDetailed(file, output, "$indent  ", stats)
                } else {
                    stats.filesCount++
                    if (size > 0) stats.totalSize += size

                    val fileInfo = buildString {
                        append("$indent📄 ${file.name}")
                        if (size >= 0) {
                            append(" (${formatFileSize(size)})")
                        }
                        if (Files.isHidden(path)) {
                            append(" [Oculto]")
                        }
                        append("\n")
                    }
                    output.append(fileInfo)

                    // Analizar contenido según el tipo de archivo
                    when (file.extension.lowercase(Locale.getDefault())) {
                        "xml", "iml", "properties", "gradle", "kt", "java" -> {
                            try {
                                val content = file.readText()
                                output.append("$indent    └─ Primeros 200 caracteres:\n")
                                output.append("$indent      ${content.take(200).replace("\n", "\n$indent      ")}...\n")
                            } catch (e: Exception) {
                                output.append("$indent    └─ No se pudo leer el contenido: ${e.message}\n")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            output.append("$indent❌ [Error al escanear: ${e.message}]\n")
        }
    }

    private fun scanProjectStructure(project: Project, output: StringBuilder) {
        val projectRoot = ProjectRootManager.getInstance(project)

        output.append("Módulos y Dependencias:\n")
        projectRoot.contentRoots.forEach { root ->
            output.append("  📂 Raíz de contenido: ${root.path}\n")
            scanVirtualFile(root, output, "    ")
        }
    }

    private fun scanVirtualFile(file: VirtualFile, output: StringBuilder, indent: String) {
        val icon = if (file.isDirectory) "📁" else "📄"
        output.append("$indent$icon ${file.name}\n")
        if (file.isDirectory) {
            file.children.forEach { child ->
                scanVirtualFile(child, output, "$indent  ")
            }
        }
    }

    private fun saveReport(basePath: String, content: String) {
        try {
            val reportFileName = "project_scan_${getCurrentDateTime("yyyyMMdd_HHmmss")}.txt"
            val reportFile = File(basePath, reportFileName)
            reportFile.writeText(content)

            // Versión HTML
            val htmlContent = convertToHtml(content)
            val htmlReportFile = File(basePath, "${reportFileName.removeSuffix(".txt")}.html")
            htmlReportFile.writeText(htmlContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun convertToHtml(content: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Análisis del Proyecto</title>
                <style>
                    body { font-family: monospace; padding: 20px; background-color: #f5f5f5; }
                    .container { background-color: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    pre { background-color: #f8f9fa; padding: 15px; border-radius: 5px; overflow-x: auto; }
                    .directory { color: #0366d6; }
                    .file { color: #24292e; }
                    .size { color: #6a737d; }
                    .content { color: #22863a; margin-left: 20px; border-left: 2px solid #e1e4e8; padding-left: 10px; }
                    .error { color: #cb2431; }
                    .stats { background-color: #f1f8ff; padding: 10px; margin: 10px 0; border-radius: 5px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <pre>${
            content.replace("<", "&lt;").replace(">", "&gt;")
                .replace("📁", "🗂️")
                .replace("📄", "📝")
                .replace("❌", "⚠️")
        }</pre>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun showSummaryDialog(project: Project, stats: ScanStats, backupPath: String) {
        val message = """
            Análisis completado:
            - Archivos encontrados: ${stats.filesCount}
            - Directorios encontrados: ${stats.directoriesCount}
            - Tamaño total: ${formatFileSize(stats.totalSize)}
            
            Se han generado archivos en la raíz del proyecto:
            - Reporte en texto plano (.txt)
            - Reporte en formato HTML (.html)
            
            Se ha creado una copia completa del proyecto en:
            $backupPath
            
            Esta copia contiene todos los archivos y carpetas del proyecto
            original, manteniendo la estructura exacta.
        """.trimIndent()

        Messages.showMessageDialog(
            project,
            message,
            "Análisis y Copia Completados",
            Messages.getInformationIcon()
        )
    }

    private fun formatFileSize(size: Long): String {
        if (size < 0) return "Tamaño desconocido"
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun getCurrentDateTime(pattern: String = "dd/MM/yyyy HH:mm:ss"): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern))
    }
}