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
  fun save(source: String, to: File)

  /** Merges the given XML source into the given destination file (or just writes it if the destination file does not exist). */
  fun mergeXml(source: String, to: File)

  /** Merges the given Gradle source into the given destination file (or just writes it if the destination file does not exist). */
  @Deprecated("Avoid merging Gradle files, add to an existing file programmatically instead")
  fun mergeGradleFile(source: String, to: File)

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
   * Records a library dependency (adds an new entry to.
   * "compile" configuration is used by default for backward compatibility.
   * It is converted to "implementation" in later stages if Gradle supports it.
   * */
  fun addDependency(mavenCoordinate: String, configuration: String = "compile")

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