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
import com.android.builder.model.level2.Library.LIBRARY_MODULE
import com.android.ide.common.gradle.model.stubs.BaseStub
import java.io.File
import java.lang.UnsupportedOperationException

class ModuleLibraryStub(
  private val type : Int,
  private val artifactAddress : String,
  private val buildId : String?,
  private val projectPath : String?,
  private val variant : String?
) : BaseStub(), Library {
  private fun unsupported(method: String): Nothing =
    throw UnsupportedOperationException("$method() cannot be called when getType() returns LIBRARY_MODULE")

  override fun getType(): Int = type
  override fun getArtifactAddress(): String = artifactAddress
  override fun getArtifact(): File = unsupported("getArtifact")
  override fun getBuildId(): String? = buildId
  override fun getProjectPath(): String? = projectPath
  override fun getVariant(): String? = variant
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

class ModuleLibraryStubBuilder(
  var type : Int = LIBRARY_MODULE,
  var artifactAddress : String = "artifact:address:1.0",
  var buildId : String? = null,
  var projectPath : String? = null,
  var variant : String? = null
) {
  fun build() = ModuleLibraryStub(
    type = type,
    artifactAddress = artifactAddress,
    buildId = buildId,
    projectPath = projectPath,
    variant = variant
  )
}