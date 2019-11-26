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

/** Execution engine for the instructions in a recipe. */
interface RecipeExecutor {
  /**
   * Copies the given source file into the given destination file (where the
   * source is allowed to be a directory, in which case the whole directory is copied recursively).
   */
  fun copy(from: File, to: File)

  /** Writes text into the given output file. */
  fun save(source: String, to: File, trimVertical: Boolean = true, squishEmptyLines: Boolean = true)

  /** Merges the given XML source into the given destination file (or just writes it if the destination file does not exist). */
  fun mergeXml(source: String, to: File)

  /**
   * Creates a directory at the specified location (if not already present).
   * This will also create any parent directories that don't exist, as well.
   */
  fun createDirectory(at: File)

  /** Records that this file should be opened in Studio. */
  fun open(file: File)

  /** Adds "apply plugin: '`plugin`'" statement to the module build.gradle file. */
  fun applyPlugin(plugin: String)

  /** Records a classpath dependency. */
  fun addClasspathDependency(mavenCoordinate: String)

  /** Determines if a module/project already have a dependency. */
  fun hasDependency(mavenCoordinate: String, configuration: String? = null): Boolean

  /**
   * Records a library dependency
   * Old [configuration]s such as "compile" will be converted to new ones ("implementation") in later stages if Gradle supports it.
   *
   * @param mavenCoordinate coordinate of dependency to be added in Maven format (e.g androidx.appcompat:appcompat:1.1.0).
   * @param configuration Gradle configuration to use.
   * */
  fun addDependency(mavenCoordinate: String, configuration: String = "compile")

  /**
   * Records a module dependency.
   * Old [configuration]s such as "compile" will be converted to new ones ("implementation") in later stages if Gradle supports it.
   *
   * @param configuration Gradle configuration to use.
   * @param moduleName name of a module on which something depends. Should not start with ':'.
   * @param toModule name of a module *directory* which depends on [moduleName].
   */
  fun addModuleDependency(configuration: String, moduleName: String, toModule: String)

  /**
   * Adds a new entry to 'sourceSets' block of Gradle build file.
   *
   * @param type type of the source set.
   * @param name source set name that is created/modified.
   * @param dir path to the source set folder (or file if [type] is [SourceSetType.MANIFEST]).
   * */
  fun addSourceSet(type: SourceSetType, name: String, dir: File)

  /** Initializes the variable with [name] to [value] in the ext block of global Gradle build file. */
  fun setExtVar(name: String, value: Any)

  /**
   * Adds a module dependency to global settings.gradle[.kts] file.
   */
  fun addIncludeToSettings(moduleName: String)

  /**
   * Adds a new build feature to android block. For example, may enable compose.
   */
  fun setBuildFeature(name: String, value: Boolean)

  /**
   * Sets sourceCompatibility and targetCompatibility in compileOptions and (if needed) jvmTarget in kotlinOptions.
   */
  fun requireJavaVersion(version: String, kotlinSupport: Boolean = false)
}

enum class SourceSetType {
  AIDL,
  ASSETS,
  JAVA,
  JNI,
  MANIFEST,
  RENDERSCRIPT,
  RES,
  RESOURCES
}