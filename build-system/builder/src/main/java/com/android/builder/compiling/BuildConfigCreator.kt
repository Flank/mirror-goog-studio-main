package com.android.builder.compiling

import java.io.File
import java.io.IOException

interface BuildConfigCreator {
    val folderPath : File
    val buildConfigFile : File

    @Throws(IOException::class)
    fun generate()
}