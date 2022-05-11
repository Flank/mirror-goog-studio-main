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

package com.android.build.gradle.internal.privaysandboxsdk

import com.android.build.api.dsl.PrivacySandboxSdkExtension
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScope
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.specs.Spec

class PrivacySandboxSdkVariantScope(
    project: Project,
    extensionProvider: () -> PrivacySandboxSdkExtension
): FusedLibraryVariantScope(project, extensionProvider) {

    override val mergeSpec = Spec { componentIdentifier: ComponentIdentifier ->
        println("In mergeSpec -> $componentIdentifier, type is ${componentIdentifier.javaClass}, merge = ${componentIdentifier is ProjectComponentIdentifier}")
        true // so far, all dependencies are consumed by the sdk library plugin.
    }

}
