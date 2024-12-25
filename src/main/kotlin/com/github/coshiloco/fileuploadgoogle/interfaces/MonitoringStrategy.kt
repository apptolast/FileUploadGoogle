package com.github.coshiloco.fileuploadgoogle.interfaces

import com.intellij.openapi.vfs.newvfs.events.VFileEvent

interface MonitoringStrategy {
    fun shouldMonitor(event: VFileEvent): Boolean
}