package com.github.coshiloco.fileuploadgoogle.interfaces

import com.github.coshiloco.fileuploadgoogle.models.BackupResult


interface BackupStrategy {
    fun backup(source: String, destination: String): BackupResult
}
