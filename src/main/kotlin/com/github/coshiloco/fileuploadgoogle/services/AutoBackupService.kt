package com.github.coshiloco.fileuploadgoogle.services

import com.github.coshiloco.fileuploadgoogle.actions.BackupAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.MessageBusConnection
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

@Service(Service.Level.PROJECT)
class AutoBackupService(private val project: Project) {
    private var connection: MessageBusConnection? = null
    private var scheduler: ScheduledExecutorService? = null
    private var scheduledBackup: ScheduledFuture<*>? = null
    private var isRunning = false

    fun startMonitoring() {
        if (isRunning) return
        isRunning = true

        // Iniciar el scheduler
        scheduler = Executors.newSingleThreadScheduledExecutor()

        // Programar backup cada 5 minutos
        scheduledBackup = scheduler?.scheduleAtFixedRate({
            performBackup()
        }, 0, 5, TimeUnit.MINUTES)

        // Suscribirse a cambios en archivos
        connection = project.messageBus.connect()
        connection?.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { shouldTriggerBackup(it) }) {
                    performBackup()
                }
            }
        })
    }

    fun stopMonitoring() {
        isRunning = false
        scheduledBackup?.cancel(false)
        scheduler?.shutdown()
        connection?.disconnect()
    }

    private fun shouldTriggerBackup(event: VFileEvent): Boolean {
        // Ignorar cambios en la carpeta de backup
        if (event.path.contains("_copia")) return false

        // Ignorar archivos temporales y de sistema
        val ignoredPatterns = listOf(
            ".tmp", ".log", ".lock",
            "/.idea/", "/build/", "/out/"
        )

        return !ignoredPatterns.any { event.path.contains(it) }
    }

    private fun performBackup() {
        // Usar la lógica existente de BackupAction
        project.basePath?.let { basePath ->
            BackupAction().executeBackup(project, basePath)
        }
    }
}