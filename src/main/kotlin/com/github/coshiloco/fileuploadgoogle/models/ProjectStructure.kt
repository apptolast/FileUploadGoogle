package com.github.coshiloco.fileuploadgoogle.models

data class ProjectStructure(
    val physicalStructure: List<FileNode>,
    val logicalStructure: List<FileNode>
)
