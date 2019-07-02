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

// TODO: change it to enum, wrapper, sealed class or at least UShort
typealias Version = Int

// TODO: use wrappers/similar to check validity?
typealias PackageName = String
typealias GradlePluginVersion = String
typealias JavaVersion = String
typealias Revision = String

enum class Language(val string: String, val extension: String) {
  Java("Java", "java"),
  Kotlin("Kotlin", "kt")
}

// TODO: pack version data in separate class, possibly similar to AndroidVersionsInfo.VersionItem
data class ProjectTemplateData(
  val minApi: String,
  val minApiLevel: Version,
  val buildApi: Version,
  val androidXSupport: Boolean,
  val targetApi: Version,
  /**
   * Not null only if it is unreleased yet API.
   */
  val targetApiString: String?,
  val buildApiString: String,
  val buildApiRevision: Int,
  val gradlePluginVersion: GradlePluginVersion,
  val javaVersion: JavaVersion?,
  val sdkDir: File,
  val language: Language,
  val kotlinVersion: String,
  val buildToolsVersion: Revision,
  val rootDir: File,
  val applicationPackage: PackageName?
): TemplateData()


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
  val aidlDir: File,
  val rootDir: File,
  val themeExists: Boolean,
  val isNew: Boolean,
  val hasApplicationTheme: Boolean,
  val name: String,
  val isLibrary: Boolean,
  val packageName: PackageName,
  val formFactor: FormFactor,
  val themesData: ThemesData,
  /**
   * Info about base feature. Only present in dynamic feature project.
   */
  val baseFeature: BaseFeature?
): TemplateData()
