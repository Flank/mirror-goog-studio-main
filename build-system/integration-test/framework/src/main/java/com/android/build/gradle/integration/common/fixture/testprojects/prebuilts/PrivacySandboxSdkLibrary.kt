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

package com.android.build.gradle.integration.common.fixture.testprojects.prebuilts

import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.TestProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.options.BooleanOption


fun createGradleProjectWithPrivacySandboxLibrary(action: TestProjectBuilder.() -> Unit) = createGradleProjectBuilder {
    subProject(":privacy-sandbox-sdk") {
        plugins.add(PluginType.PRIVACY_SANDBOX_SDK)
        android {
            defaultCompileSdk()
            namespace = "com.example.privacysandboxsdk"
            minSdk = 33
        }
        dependencies {
            include(project(":privacy-sandbox-sdk-impl"))
        }
        appendToBuildFile {
            """
                android {
                    bundle {
                        applicationId = "com.example.privacysandboxsdk"
                        sdkProviderClassName = "Test"
                        setVersion(1, 2, 3)
                    }
                }
            """.trimIndent()
        }
    }
    subProject(":privacy-sandbox-sdk-impl") {
        plugins.add(PluginType.ANDROID_LIB)
        android {
            defaultCompileSdk()
            namespace = "com.example.privacysandboxsdk.impl"
            minSdk = 33
        }
    }
    action(this)
}.addGradleProperties("${BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.propertyName}=true")
    .create()



