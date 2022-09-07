/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.BuildToolsExecutableInput
import org.apache.commons.io.FileUtils
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

@DisableCachingByDefault
abstract class ExtractSdkShimTransform: TransformAction<ExtractSdkShimTransform.Parameters> {

    interface Parameters: GenericTransformParameters {

        // This is temporary until permanent method of getting apigenerator dependencies is finished.
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.NONE)
        val apiGeneratorAndRuntimeDependenciesJars: ConfigurableFileCollection

        @get:Nested
        val buildTools: BuildToolsExecutableInput
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {
        val sdkInterfaceDescriptorJar = inputArtifact.get().asFile
        val output = transformOutputs.dir("${sdkInterfaceDescriptorJar.nameWithoutExtension}-generated")
        FileUtils.cleanDirectory(output)

        if (!sdkInterfaceDescriptorJar.isFile) {
            throw IOException("${sdkInterfaceDescriptorJar.absolutePath} must be a file.")
        }

        val aidlExecutable = parameters.buildTools.aidlExecutableProvider().get().absoluteFile

        val apiGeneratorJarsFiles = parameters.apiGeneratorAndRuntimeDependenciesJars.files
                ?: error("No jar files founds for dependencies of apigenerator")
        val apiGeneratorUrls: Array<URL> =
                apiGeneratorJarsFiles.mapNotNull {it.toURI().toURL()}.toTypedArray()
        URLClassLoader(apiGeneratorUrls).use {
            Generator(it).generate(
                    sdkInterfaceDescriptors = sdkInterfaceDescriptorJar.toPath(),
                    aidlCompiler = aidlExecutable.toPath(),
                    outputDirectory = output.toPath())
        }
    }

    class Generator(val classLoader: URLClassLoader) {
        private val apiGeneratorPackage = "androidx.privacysandbox.tools.apigenerator"
        private val privacySandboxApiGeneratorClass =
                loadClass("$apiGeneratorPackage.PrivacySandboxApiGenerator")
        private val privacySandboxSdkGenerator = privacySandboxApiGeneratorClass
                .getConstructor()
                .newInstance()
        private val generateMethod = privacySandboxApiGeneratorClass
                .getMethod("generate", Path::class.java, Path::class.java, Path::class.java)

        fun generate(
                sdkInterfaceDescriptors: Path,
                aidlCompiler: Path,
                outputDirectory: Path) {
            generateMethod.invoke(privacySandboxSdkGenerator,
                            sdkInterfaceDescriptors, aidlCompiler, outputDirectory)
        }
        private fun loadClass(classToLoad: String) = classLoader.loadClass(classToLoad)
    }
}
