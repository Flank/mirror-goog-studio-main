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
import com.android.ide.common.gradle.model.level2.IdeDependencies
import java.io.File

class IdeDependenciesStub(
  private val androidLibraries : Collection<Library>,
  private val javaLibraries : Collection<Library>,
  private val moduleDependencies : Collection<Library>,
  private val runtimeOnlyClasses : Collection<File>
) : IdeDependencies {
  override fun getAndroidLibraries(): Collection<Library> = androidLibraries
  override fun getJavaLibraries(): Collection<Library> = javaLibraries
  override fun getModuleDependencies(): Collection<Library> = moduleDependencies
  override fun getRuntimeOnlyClasses(): Collection<File> = runtimeOnlyClasses
}

class IdeDependenciesStubBuilder(
  var androidLibraries : Collection<Library> = emptyList(),
  var javaLibraries : Collection<Library> = emptyList(),
  var moduleDependencies : Collection<Library> = emptyList(),
  var runtimeOnlyClasses : Collection<File> = emptyList()
) {
  fun build() = IdeDependenciesStub(
    androidLibraries,
    javaLibraries,
    moduleDependencies,
    runtimeOnlyClasses
  )
}