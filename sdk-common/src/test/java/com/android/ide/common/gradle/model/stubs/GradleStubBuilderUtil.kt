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
@file:JvmName("GradleStubBuilderUtil")
package com.android.ide.common.gradle.model.stubs

import com.android.ide.common.gradle.model.stubs.level2.AndroidLibraryStub
import com.android.ide.common.gradle.model.stubs.level2.AndroidLibraryStubBuilder
import com.android.ide.common.gradle.model.stubs.level2.IdeDependenciesStub
import com.android.ide.common.gradle.model.stubs.level2.IdeDependenciesStubBuilder
import com.android.ide.common.gradle.model.stubs.level2.JavaLibraryStub
import com.android.ide.common.gradle.model.stubs.level2.JavaLibraryStubBuilder
import com.android.ide.common.gradle.model.stubs.level2.ModuleLibraryStub
import com.android.ide.common.gradle.model.stubs.level2.ModuleLibraryStubBuilder

fun l2AndroidLibrary(address: String) : AndroidLibraryStub =
  AndroidLibraryStubBuilder(artifactAddress = address).build()

fun l2JavaLibrary(address: String) : JavaLibraryStub =
  JavaLibraryStubBuilder(artifactAddress = address).build()

fun l2ModuleLibrary(address: String) : ModuleLibraryStub =
  ModuleLibraryStubBuilder(artifactAddress = address).build()
