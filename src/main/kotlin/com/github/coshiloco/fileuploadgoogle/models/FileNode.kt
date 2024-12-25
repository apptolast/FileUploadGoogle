package com.github.coshiloco.fileuploadgoogle.models

import com.github.coshiloco.fileuploadgoogle.enums.FileType


data class FileNode(
    val name: String,
    val path: String,
    val type: FileType,
    val size: Long,
    val content: String? = null,
    val children: List<FileNode> = emptyList()
)

