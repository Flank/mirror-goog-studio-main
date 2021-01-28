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
package com.android.tools.idea.wizard.template

import java.io.File

/**
 * Data which is required for template rendering.
 *
 * One of implementations of it will be passed to the renderer (template's recipe).
 **/
sealed class TemplateData

/** apiLevelString usually has the value of apiLevel (as a String), but may be a "name" for unreleased APIs. */
data class  ApiVersion(val api: Int, val apiString: String)

// TODO: use wrappers/similar to check validity?
typealias PackageName = String
typealias GradlePluginVersion = String
typealias JavaVersion = String
typealias Revision = String
typealias FormFactorNames = Map<FormFactor, List<String>>
enum class Language(val string: String, val extension: String) {
  Java("Java", "java"),
  Kotlin("Kotlin", "kt");

  override fun toString(): String = string

  companion object {
    /**
     * Finds a language matching the requested name. Returns specified 'defaultValue' if not found.
     */
    @JvmStatic
    fun fromName(name: String?, defaultValue: Language): Language =
      values().firstOrNull { it.string == name } ?: defaultValue
  }
}
// We define a new enum here instead of reusing existing ones because it should be available
// both from intellij.android.core and wizardTemplate modules.
enum class BytecodeLevel(val description: String, val versionString: String) {
  L6("6 (fewer features)", "1.6"),
  L7("7", "1.7"),
  L8("8 (slower build)", "1.8");

  override fun toString() = description

  companion object {
    val default: BytecodeLevel get() = L8
  }
}

data class ApiTemplateData(
  val buildApi: ApiVersion,
  val targetApi: ApiVersion,
  val minApi: ApiVersion,
  val appCompatVersion: Int
)

// TODO: pack version data in separate class, possibly similar to AndroidVersionsInfo.VersionItem
data class ProjectTemplateData(
  val androidXSupport: Boolean,
  val gradlePluginVersion: GradlePluginVersion,
  val sdkDir: File?,
  val language: Language,
  val kotlinVersion: String,
  val buildToolsVersion: Revision,
  val rootDir: File,
  val applicationPackage: PackageName?,
  val includedFormFactorNames: FormFactorNames,
  val debugKeystoreSha1: String?,
  val overridePathCheck: Boolean? = false, // To disable android plugin checking for ascii in paths (windows tests)
  val isNewProject: Boolean
): TemplateData()

fun FormFactorNames.has(ff: FormFactor) = !this[ff].isNullOrEmpty()

// TODO(qumeric): create a more generic mechanism which will support modifying other modules
/**
 * Info about base feature.
 *
 * When we have dynamic feature project, Studio may need to add something to base feature module even when
 * Studio does not create something directly inside of the module. For example, Studio may do it when creating a new dynamic module or
 * an activity inside dynamic module.
 */
data class BaseFeature(
  val name: String,
  val dir: File,
  val resDir: File
)

data class ModuleTemplateData(
  val projectTemplateData: ProjectTemplateData,
  val srcDir: File,
  val resDir: File,
  val manifestDir: File,
  val testDir: File,
  val unitTestDir: File,
  val aidlDir: File,
  val rootDir: File,
  val isNewModule: Boolean,
  val name: String,
  val isLibrary: Boolean,
  val packageName: PackageName,
  val formFactor: FormFactor,
  val themesData: ThemesData,
  /**
   * Info about base feature. Only present in dynamic feature project.
   */
  val baseFeature: BaseFeature?,
  val apis: ApiTemplateData,
  val viewBindingSupport: ViewBindingSupport
): TemplateData() {
  val isDynamic: Boolean
    get() = baseFeature != null
}

/**
 * enum class representing if a module supports view binding.
 * Need to have different values for AGP3.6 and AGP4.0+ because they have different syntax.
 */
enum class ViewBindingSupport {
  NOT_SUPPORTED,
  SUPPORTED_3_6,
  SUPPORTED_4_0_MORE;

  fun isViewBindingSupported(): Boolean = this == SUPPORTED_3_6 || this == SUPPORTED_4_0_MORE
}

enum class CppStandardType(val compilerFlag: String) {
  `Toolchain Default`(""),
  `C++11`("-std=c++11"),
  `C++14`("-std=c++14"),
  `C++17`("-std=c++17");

  override fun toString(): String {
    return compilerFlag
  }
}
