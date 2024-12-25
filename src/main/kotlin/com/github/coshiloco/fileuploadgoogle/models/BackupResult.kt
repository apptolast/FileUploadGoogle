package com.github.coshiloco.fileuploadgoogle.models

data class BackupResult(
    val success: Boolean,
    val message: String,
    val backupPath: String?
)
