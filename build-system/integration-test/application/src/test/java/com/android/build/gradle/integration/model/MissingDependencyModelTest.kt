/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.model

import com.android.Version
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.internal.ide.v2.UnresolvedDependencyImpl
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class MissingDependencyModelTest {

    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
            dependencies {
                implementation("foo:bar:1.1")
            }
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        Truth.assertThat(
            result.container.getProject().variantDependencies?.mainArtifact?.unresolvedDependencies?.map {
                UnresolvedDependencyImpl(it.name, it.cause)
            }
        ).containsExactly(UnresolvedDependencyImpl("foo:bar:1.1", null))
    }
}

class UnresolvedVariantDependencyModelTest {

    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                buildTypes {
                    named("staging") {}
                }
            }
            dependencies {
                implementation(project(":lib"))
            }
        }
        subProject(":lib") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                setUpHelloWorld()
            }
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "staging")

        val appInfo = result.container.rootInfoMap[":app"] ?: throw RuntimeException("No app info")
        val variantDependencies =
            appInfo.variantDependencies ?: throw RuntimeException("No variant dep")

        Truth.assertThat(
            variantDependencies.mainArtifact.unresolvedDependencies.map {
                UnresolvedDependencyImpl(it.name, it.cause?.fixLineEndings()?.fixAgpVersion())
            }
        ).containsExactly(
            UnresolvedDependencyImpl(
                "project :lib",
                """
No matching variant of project :lib was found. The consumer was configured to find an API of a component, preferably optimized for Android, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'staging', attribute 'com.android.build.api.attributes.AgpVersionAttr' with value '{AGP-VERSION}' but:
  - Variant 'debugApiElements' capability project:lib:unspecified declares an API of a component, as well as attribute 'com.android.build.api.attributes.AgpVersionAttr' with value '{AGP-VERSION}':
      - Incompatible because this component declares a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'debug' and the consumer needed a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'staging'
      - Other compatible attribute:
          - Doesn't say anything about its target Java environment (preferred optimized for Android)
  - Variant 'debugRuntimeElements' capability project:lib:unspecified declares a runtime of a component, as well as attribute 'com.android.build.api.attributes.AgpVersionAttr' with value '{AGP-VERSION}':
      - Incompatible because this component declares a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'debug' and the consumer needed a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'staging'
      - Other compatible attribute:
          - Doesn't say anything about its target Java environment (preferred optimized for Android)
  - Variant 'releaseApiElements' capability project:lib:unspecified declares an API of a component, as well as attribute 'com.android.build.api.attributes.AgpVersionAttr' with value '{AGP-VERSION}':
      - Incompatible because this component declares a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'release' and the consumer needed a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'staging'
      - Other compatible attribute:
          - Doesn't say anything about its target Java environment (preferred optimized for Android)
  - Variant 'releaseRuntimeElements' capability project:lib:unspecified declares a runtime of a component, as well as attribute 'com.android.build.api.attributes.AgpVersionAttr' with value '{AGP-VERSION}':
      - Incompatible because this component declares a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'release' and the consumer needed a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'staging'
      - Other compatible attribute:
          - Doesn't say anything about its target Java environment (preferred optimized for Android)""".trimIndent()
            )
        )
    }
}

private fun String.fixLineEndings(): String = this.replace("\r\n", "\n")

private fun String.fixAgpVersion(): String = this.replace(Version.ANDROID_GRADLE_PLUGIN_VERSION, "{AGP-VERSION}")
