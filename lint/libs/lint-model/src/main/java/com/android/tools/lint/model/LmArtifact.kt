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

package com.android.tools.lint.model

import java.io.File

interface LmArtifact {
    val name: String
    val dependencies: LmDependencies
    val classFolders: List<File>
}

interface LmJavaArtifact : LmArtifact

interface LmAndroidArtifact : LmArtifact {
    val applicationId: String
    val generatedResourceFolders: Collection<File>
    val generatedSourceFolders: Collection<File>
}

open class DefaultLmArtifact(
    override val name: String,
    override val dependencies: LmDependencies,
    override val classFolders: List<File>
) : LmArtifact

class DefaultLmJavaArtifact(
    name: String,
    classFolders: List<File>,
    dependencies: LmDependencies
) : DefaultLmArtifact(
    name = name,
    dependencies = dependencies,
    classFolders = classFolders
), LmJavaArtifact

class DefaultLmAndroidArtifact(
    name: String,
    override val applicationId: String,
    override val generatedResourceFolders: Collection<File>,
    override val generatedSourceFolders: Collection<File>,
    classFolders: List<File>,
    dependencies: LmDependencies
) : DefaultLmArtifact(
    name = name,
    dependencies = dependencies,
    classFolders = classFolders
), LmAndroidArtifact
