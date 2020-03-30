package com.android.builder.compiling

import java.io.File
import java.io.IOException

interface BuildConfigCreator {
    fun getFolderPath() : File
    fun getBuildConfigFile() : File

    @Throws(IOException::class)
    fun generate()
}