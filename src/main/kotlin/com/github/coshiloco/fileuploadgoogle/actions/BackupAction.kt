package com.github.coshiloco.fileuploadgoogle.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.io.File

class BackupAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            Messages.showErrorDialog("No project found", "Error")
            return
        }

        // Obtener la ruta base del proyecto
        val basePath = project.basePath
        if (basePath == null) {
            Messages.showErrorDialog("No project path found", "Error")
            return
        }

        // Escanear el proyecto
        val projectStructure = scanDirectory(File(basePath))

        // Mostrar resultados
        Messages.showMessageDialog(
            project,
            "Estructura del proyecto:\n$projectStructure",
            "Project Scan Result",
            Messages.getInformationIcon()
        )
    }

    private fun scanDirectory(directory: File, indent: String = ""): String {
        val result = StringBuilder()
        directory.listFiles()?.forEach { file ->
            result.append("$indent${file.name}\n")
            if (file.isDirectory) {
                result.append(scanDirectory(file, "$indent  "))
            }
        }
        return result.toString()
    }
}