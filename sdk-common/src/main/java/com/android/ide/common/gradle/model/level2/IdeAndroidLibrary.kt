/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.ide.common.gradle.model.level2

import java.io.File

/**
 * The implementation of IdeLibrary for Android libraries.
 **/
data class IdeAndroidLibrary(
  override val artifactAddress: String,
  override val folder: File?,
  override val manifest: String,
  override val jarFile: String,
  override val compileJarFile: String,
  override val resFolder: String,
  override val resStaticLibrary: File?,
  override val assetsFolder: String,
  override val localJars: Collection<String>,
  override val jniFolder: String,
  override val aidlFolder: String,
  override val renderscriptFolder: String,
  override val proguardRules: String,
  override val lintJar: String,
  override val externalAnnotations: String,
  override val publicResources: String,
  override val artifact: File,
  override val symbolFile: String,
  override val isProvided: Boolean
) : IdeLibrary {

  // Used for serialization by the IDE.
  internal constructor() : this(
    artifactAddress = "",
    folder = null,
    manifest = "",
    jarFile = "",
    compileJarFile = "",
    resFolder = "",
    resStaticLibrary = null,
    assetsFolder = "",
    localJars = mutableListOf(),
    jniFolder = "",
    aidlFolder = "",
    renderscriptFolder = "",
    proguardRules = "",
    lintJar = "",
    externalAnnotations = "",
    publicResources = "",
    artifact = File(""),
    symbolFile = "",
    isProvided = false
  )

  override val type: IdeLibrary.LibraryType
    get() = IdeLibrary.LibraryType.LIBRARY_ANDROID

  override val variant: String?
    get() = throw unsupportedMethodForAndroidLibrary("getVariant")

  override val buildId: String?
    get() = throw unsupportedMethodForAndroidLibrary("getBuildId")

  override val projectPath: String
    get() = throw unsupportedMethodForAndroidLibrary("getProjectPath")
}

private fun unsupportedMethodForAndroidLibrary(methodName: String): UnsupportedOperationException =
  UnsupportedOperationException("$methodName() cannot be called when getType() returns ANDROID_LIBRARY")
