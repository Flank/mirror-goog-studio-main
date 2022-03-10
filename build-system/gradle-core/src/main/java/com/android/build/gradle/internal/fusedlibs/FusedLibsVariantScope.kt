/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.fusedlibs

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.FusedLibrariesExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ProjectLayout
import org.gradle.api.specs.Spec

class FusedLibsVariantScope(
    project: Project,
    extensionProvider: () -> FusedLibrariesExtension
) {
    val layout: ProjectLayout = project.layout
    val artifacts= ArtifactsImpl(project, "single")
    val incomingConfigurations = FusedLibsConfigurations()
    val outgoingConfigurations = FusedLibsConfigurations()

    val extension: FusedLibrariesExtension by lazy {
        extensionProvider.invoke()
    }

    val mergeSpec = Spec { componentIdentifier: ComponentIdentifier ->
        println("In mergeSpec -> $componentIdentifier, type is ${componentIdentifier.javaClass}, merge = ${componentIdentifier is ProjectComponentIdentifier}")
        componentIdentifier is ProjectComponentIdentifier
    }
}
