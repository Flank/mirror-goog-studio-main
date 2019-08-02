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
import com.android.builder.model.level2.Library.LIBRARY_JAVA
import com.android.ide.common.gradle.model.stubs.BaseStub
import java.io.File

class JavaLibraryStub(
  private val type : Int,
  private val artifactAddress : String,
  private val artifactFile : File
) : BaseStub(), Library {
  private fun unsupported(method: String): Nothing =
    throw UnsupportedOperationException("$method() cannot be called when getType() returns LIBRARY_JAVA")

  override fun getType(): Int = type
  override fun getArtifactAddress(): String = artifactAddress
  override fun getArtifact(): File = artifactFile
  override fun getBuildId(): String? = unsupported("getBuildId")
  override fun getProjectPath(): String? = unsupported("getProjectPath")
  override fun getVariant(): String? = unsupported("getVariant")
  override fun getFolder(): File = unsupported("getFolder")
  override fun getManifest(): String = unsupported("getManifest")
  override fun getJarFile(): String = unsupported("getJarFile")
  override fun getCompileJarFile(): String = unsupported("getCompileJarFile")
  override fun getResFolder(): String = unsupported("getResFolder")
  override fun getResStaticLibrary(): File? = unsupported("getResStaticLibrary")
  override fun getAssetsFolder(): String = unsupported("getAssetsFolder")
  override fun getLocalJars(): MutableCollection<String> = unsupported("getLocalJars")
  override fun getJniFolder(): String = unsupported("getJniFolder")
  override fun getAidlFolder(): String = unsupported("getAidlFolder")
  override fun getRenderscriptFolder(): String = unsupported("getRenderscriptFolder")
  override fun getProguardRules(): String = unsupported("getProguardRules")
  override fun getLintJar(): String = unsupported("getLintJar")
  override fun getExternalAnnotations(): String = unsupported("getExternalAnnotations")
  override fun getPublicResources(): String = unsupported("getPublicResources")
  override fun getSymbolFile(): String = unsupported("getSymbolFile")
}

class JavaLibraryStubBuilder(
  var type : Int = LIBRARY_JAVA,
  var artifactAddress : String = "artifact:address:1.0",
  var artifactFile : File = File("artifactFile")
) {
  fun build() = JavaLibraryStub(
    type = type,
    artifactAddress = artifactAddress,
    artifactFile = artifactFile
  )
}