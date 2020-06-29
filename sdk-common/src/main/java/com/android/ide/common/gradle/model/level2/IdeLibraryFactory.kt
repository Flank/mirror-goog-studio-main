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

import com.android.SdkConstants
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.JavaLibrary
import com.android.ide.common.gradle.model.IdeLibraries
import com.android.ide.common.gradle.model.IdeModel
import com.google.common.annotations.VisibleForTesting
import java.io.File

/**
 * Creates instance of [IdeLibrary].
 **/
class IdeLibraryFactory {
  /**
   * @param androidLibrary Instance of [AndroidLibrary] returned by android plugin.
   * @param moduleBuildDirs Instance of [BuildFolderPaths] that contains map from project
   * path to build directory for all modules.
   * @return Instance of [Library] based on dependency type.
   */
  @VisibleForTesting
  fun create(androidLibrary: AndroidLibrary, moduleBuildDirs: BuildFolderPaths): IdeLibrary {
    // If the dependency is a sub-module that wraps local aar, it should be considered as external dependency, i.e. type LIBRARY_ANDROID.
    // In AndroidLibrary, getProject() of such dependency returns non-null project name, but they should be converted to IdeLevel2AndroidLibrary.
    // Identify such case with the location of aar bundle.
    // If the aar bundle is inside of build directory of sub-module, then it's regular library module dependency, otherwise it's a wrapped aar module.
    return if (androidLibrary.project != null && !IdeLibraries.isLocalAarModule(androidLibrary, moduleBuildDirs)) {
      createIdeModuleLibrary(androidLibrary, IdeLibraries.computeAddress(androidLibrary))
    }
    else {
      IdeAndroidLibrary(
        artifactAddress = IdeLibraries.computeAddress(androidLibrary),
        folder = androidLibrary.folder,
        manifest = androidLibrary.manifest.path,
        jarFile = androidLibrary.jarFile.path,
        compileJarFile = defaultValueIfNotPresent({ androidLibrary.compileJarFile }, androidLibrary.jarFile).path,
        resFolder = androidLibrary.resFolder.path,
        resStaticLibrary = defaultValueIfNotPresent({ androidLibrary.resStaticLibrary }, null),
        assetsFolder = androidLibrary.assetsFolder.path,
        localJars = androidLibrary.localJars.map { it.path },
        jniFolder = androidLibrary.jniFolder.path,
        aidlFolder = androidLibrary.aidlFolder.path,
        renderscriptFolder = androidLibrary.renderscriptFolder.path,
        proguardRules = androidLibrary.proguardRules.path,
        lintJar = androidLibrary.lintJar.path,
        externalAnnotations = androidLibrary.externalAnnotations.path,
        publicResources = androidLibrary.publicResources.path,
        artifact = androidLibrary.bundle,
        symbolFile = getSymbolFilePath(androidLibrary),
        isProvided = defaultValueIfNotPresent({ androidLibrary.isProvided }, false)
      )
    }
  }

  /**
   * @param javaLibrary Instance of [JavaLibrary] returned by android plugin.
   * @return Instance of [Library] based on dependency type.
   */
  @VisibleForTesting
  fun create(javaLibrary: JavaLibrary): IdeLibrary {
    val project = defaultValueIfNotPresent({ javaLibrary.project }, null)
    return if (project != null) {
      // Java modules don't have variant.
      createIdeModuleLibrary(javaLibrary, IdeLibraries.computeAddress(javaLibrary))
    }
    else {
      IdeJavaLibrary(
        IdeLibraries.computeAddress(javaLibrary),
        javaLibrary.jarFile,
        defaultValueIfNotPresent({ javaLibrary.isProvided }, false)
      )
    }
  }

  fun createIdeModuleLibrary(
    library: AndroidLibrary, artifactAddress: String): IdeLibrary {
    return IdeModuleLibrary(
      artifactAddress = artifactAddress,
      buildId = IdeModel.copyNewProperty({ library.buildId }, null),
      projectPath = IdeModel.copyNewProperty({ library.project }, null),
      variant = IdeModel.copyNewProperty({ library.projectVariant }, null),
      folder = defaultValueIfNotPresent({ library.folder }, null),
      lintJar = defaultValueIfNotPresent({ library.lintJar.path }, null),
      isProvided = defaultValueIfNotPresent({ library.isProvided }, false)
    )
  }

  fun createIdeModuleLibrary(
    library: JavaLibrary, artifactAddress: String): IdeLibrary {
    return IdeModuleLibrary(
      artifactAddress = artifactAddress,
      buildId = IdeModel.copyNewProperty({ library.buildId }, null),
      projectPath = IdeModel.copyNewProperty({ library.project }, null),
      variant = null,
      folder = null,
      lintJar = null,
      isProvided = defaultValueIfNotPresent({ library.isProvided }, false)
    )
  }

  companion object {
    private fun getSymbolFilePath(androidLibrary: AndroidLibrary): String {
      return try {
        androidLibrary.symbolFile.path
      }
      catch (e: UnsupportedOperationException) {
        File(androidLibrary.folder, SdkConstants.FN_RESOURCE_TEXT).path
      }
    }

    fun <T> defaultValueIfNotPresent(propertyInvoker: () -> T, defaultValue: T): T {
      return try {
        propertyInvoker()
      }
      catch (ignored: UnsupportedOperationException) {
        defaultValue
      }
    }

    /**
     * @param projectPath Name of module dependencies.
     * @return An instance of [Library] of type LIBRARY_MODULE.
     */
    @JvmStatic
    fun create(projectPath: String, artifactAddress: String, buildId: String?): IdeLibrary {
      return IdeModuleLibrary(projectPath, artifactAddress, buildId)
    }
  }
}