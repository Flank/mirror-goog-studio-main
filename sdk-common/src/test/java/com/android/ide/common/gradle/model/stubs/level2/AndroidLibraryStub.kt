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
package com.android.ide.common.gradle.model.stubs.level2

import com.android.builder.model.level2.Library
import com.android.builder.model.level2.Library.LIBRARY_ANDROID
import com.android.ide.common.gradle.model.stubs.BaseStub
import java.io.File

data class AndroidLibraryStub(
  private val artifactAddress : String,
  private val artifactFile : File,
  private val folder : File,
  private val manifest : String,
  private val jarFile : String,
  private val compileJarFile : String,
  private val resFolder : String,
  private val resStaticLibrary : File?,
  private val assetsFolder : String,
  private val localJars : Collection<String>,
  private val jniFolder : String,
  private val aidlFolder : String,
  private val renderscriptFolder : String,
  private val proguardRules : String,
  private val lintJar : String,
  private val externalAnnotations : String,
  private val publicResources : String,
  private val symbolFile : String
) : BaseStub(), Library {
  override fun getType() : Int = LIBRARY_ANDROID
  override fun getArtifactAddress() : String = artifactAddress
  override fun getArtifact() : File = artifactFile
  override fun getFolder(): File = folder
  override fun getManifest(): String = manifest
  override fun getJarFile(): String = jarFile
  override fun getCompileJarFile(): String = compileJarFile
  override fun getResFolder(): String = resFolder
  override fun getResStaticLibrary(): File? = resStaticLibrary
  override fun getAssetsFolder(): String = assetsFolder
  override fun getLocalJars(): Collection<String> = localJars
  override fun getJniFolder(): String = jniFolder
  override fun getAidlFolder(): String = aidlFolder
  override fun getRenderscriptFolder(): String = renderscriptFolder
  override fun getProguardRules(): String = proguardRules
  override fun getLintJar(): String = lintJar
  override fun getExternalAnnotations(): String = externalAnnotations
  override fun getPublicResources(): String = publicResources
  override fun getSymbolFile(): String = symbolFile

  override fun getBuildId() : String =
    throw UnsupportedOperationException("getBuildId() cannot be called when getType() returns ANDROID_LIBRARY")
  override fun getProjectPath() : String =
    throw UnsupportedOperationException("getProjectPath() cannot be called when getType() returns ANDROID_LIBRARY")
  override fun getVariant(): String? =
    throw UnsupportedOperationException("getVariant() cannot be called when getType() returns ANDROID_LIBRARY")
}

class AndroidLibraryStubBuilder(
  var artifactAddress : String = "artifact:address:1.0",
  var artifactFile : File = File("artifactFile"),
  var folder : File = File("libraryFolder"),
  var manifest : String = "manifest.xml",
  var jarFile : String = "file.jar",
  var compileJarFile : String = "api.jar",
  var resFolder : String = "res",
  var resStaticLibrary : File? = File("libraryFolder/res.apk"),
  var assetsFolder : String = "assets",
  var localJars : Collection<String> = listOf(),
  var jniFolder : String = "jni",
  var aidlFolder : String = "aidl",
  var renderScriptFolder : String = "renderscriptFolder",
  var proguardRules : String = "proguardRules",
  var lintJar : String = "lint.jar",
  var externalAnnotations : String = "externalAnnotations",
  var publicResources : String = "publicResources",
  var symbolFile : String = "symbolFile"
) {
  fun build() = AndroidLibraryStub(
    artifactAddress = artifactAddress,
    artifactFile = artifactFile,
    folder = folder,
    manifest = manifest,
    jarFile = jarFile,
    compileJarFile = compileJarFile,
    resFolder = resFolder,
    resStaticLibrary = resStaticLibrary,
    assetsFolder = assetsFolder,
    localJars = localJars,
    jniFolder = jniFolder,
    aidlFolder = aidlFolder,
    renderscriptFolder = renderScriptFolder,
    proguardRules = proguardRules,
    lintJar = lintJar,
    externalAnnotations = externalAnnotations,
    publicResources = publicResources,
    symbolFile = symbolFile
  )
}