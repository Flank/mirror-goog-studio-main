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

import com.android.build.gradle.integration.common.fixture.model.ReferenceModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Test

class NamespaceModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                androidResources {
                    namespaced = true
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}
