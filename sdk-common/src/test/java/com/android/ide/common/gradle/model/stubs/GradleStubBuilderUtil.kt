/*
 * Copyright (C) 2019 The Android Open Source Project
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
@file:JvmName("GradleStubBuilderUtil")
package com.android.ide.common.gradle.model.stubs

import com.android.ide.common.gradle.model.impl.IdeAndroidLibrary
import com.android.ide.common.gradle.model.impl.IdeJavaLibrary
import com.android.ide.common.gradle.model.impl.IdeModuleLibrary
import java.io.File

fun l2AndroidLibrary(address: String): IdeAndroidLibrary =
  AndroidLibraryStubBuilder(artifactAddress = address).build()

fun l2JavaLibrary(address: String): IdeJavaLibrary =
  JavaLibraryStubBuilder(artifactAddress = address).build()

fun l2ModuleLibrary(projectPath: String): IdeModuleLibrary =
  ModuleLibraryStubBuilder(projectPath = projectPath).build()

class AndroidLibraryStubBuilder(
  var artifactAddress: String = "artifact:address:1.0",
  var artifactFile: File = File("artifactFile"),
  var folder: File = File("libraryFolder"),
  var manifest: String = "manifest.xml",
  var jarFile: String = "file.jar",
  var compileJarFile: String = "api.jar",
  var resFolder: String = "res",
  var resStaticLibrary: File? = File("libraryFolder/res.apk"),
  var assetsFolder: String = "assets",
  var localJars: Collection<String> = listOf(),
  var jniFolder: String = "jni",
  var aidlFolder: String = "aidl",
  var renderScriptFolder: String = "renderscriptFolder",
  var proguardRules: String = "proguardRules",
  var lintJar: String = "lint.jar",
  var externalAnnotations: String = "externalAnnotations",
  var publicResources: String = "publicResources",
  var symbolFile: String = "symbolFile",
  var isProvided: Boolean = false
) {
  fun build() = IdeAndroidLibrary(
    artifactAddress,
    folder,
    manifest,
    jarFile,
    compileJarFile,
    resFolder,
    resStaticLibrary,
    assetsFolder,
    localJars,
    jniFolder,
    aidlFolder,
    renderScriptFolder,
    proguardRules,
    lintJar,
    externalAnnotations,
    publicResources,
    artifactFile,
    symbolFile,
    isProvided
  )
}

class JavaLibraryStubBuilder(
  var artifactAddress: String = "artifact:address:1.0",
  var artifactFile: File = File("artifactFile"),
  var isProvided: Boolean = false
) {
  fun build() = IdeJavaLibrary(
    artifactAddress,
    artifactFile,
    isProvided
  )
}

class ModuleLibraryStubBuilder @JvmOverloads constructor(
  var projectPath: String,
  var artifactAddress: String = "artifact:address:1.0",
  var buildId: String? = null
) {
  fun build() = IdeModuleLibrary(
    projectPath,
    artifactAddress,
    buildId
  )
}