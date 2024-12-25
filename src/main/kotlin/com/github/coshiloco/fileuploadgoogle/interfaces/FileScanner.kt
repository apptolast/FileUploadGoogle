package com.github.coshiloco.fileuploadgoogle.interfaces

import io.github.classgraph.ScanResult

interface FileScanner {

    fun scan(path: String): ScanResult

}


