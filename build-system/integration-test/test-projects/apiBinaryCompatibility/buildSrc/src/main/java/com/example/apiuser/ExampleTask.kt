package com.example.apiuser

import com.android.build.gradle.BaseExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class ExampleTask: DefaultTask() {

    @get:InputFile
    abstract val adbExecutable: RegularFileProperty

    @get:Internal
    abstract val sdkDirectory: RegularFileProperty

    @TaskAction
    fun doThings() {
        check(sdkDirectory.get().asFile.exists()) {
            "Sdk dir $sdkDirectory exists"
        }
        print("OK")
    }

    fun configure(project: Project) {
        val baseExtension: BaseExtension = project.extensions.getByType(BaseExtension::class.java)
        adbExecutable.set( baseExtension.adbExecutable)
        sdkDirectory.set(baseExtension.sdkDirectory)
    }
}