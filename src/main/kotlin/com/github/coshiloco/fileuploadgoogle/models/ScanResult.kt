package com.github.coshiloco.fileuploadgoogle.models

data class ScanResult(
    val fileCount: Int,
    val directoryCount: Int,
    val totalSize: Long,
    val structure: ProjectStructure
)
