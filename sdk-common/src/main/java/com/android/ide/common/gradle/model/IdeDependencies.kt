/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model

import java.io.File

/**
 * Unified API for L1 ( [Dependencies] in pre-3.0 models) and L4 ([DependencyGraphs] in
 * 3.0+ models) dependencies.
 */
interface IdeDependencies {
  /**
   * Returns the Android library dependencies, both direct and transitive.
   *
   * @return the list of libraries of type LIBRARY_ANDROID.
   */
  val androidLibraries: Collection<IdeLibrary>

  /**
   * Returns the Java library dependencies, both direct and transitive dependencies.
   *
   * @return the list of libraries of type LIBRARY_JAVA.
   */
  val javaLibraries: Collection<IdeLibrary>

  /**
   * Returns the Module dependencies.
   *
   * @return the list of libraries of type LIBRARY_MODULE.
   */
  val moduleDependencies: Collection<IdeLibrary>

  /**
   * Returns the list of runtime only classes.
   *
   * @return the list of runtime only classes.
   */
  val runtimeOnlyClasses: Collection<File>
}