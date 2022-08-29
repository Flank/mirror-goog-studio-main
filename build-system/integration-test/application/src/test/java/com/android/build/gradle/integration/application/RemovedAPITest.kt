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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.builder.errors.IssueReporter
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class RemovedAPITest {

    @JvmField
    @Rule
    val project = GradleTestProject.builder().fromTestApp(
        MinimalSubProject.app("com.example.app")
    ).withPluginManagementBlock(true)
        .create()

    @Test
    fun testTransformAPI() {
        project.buildFile.appendText("""
            import com.android.build.api.transform.Format;
            import com.android.build.api.transform.QualifiedContent;
            import com.android.build.api.transform.Transform;
            import com.android.build.api.transform.TransformException;
            import com.android.build.api.transform.TransformInvocation;

            public class TestTransform extends Transform {
                public String getName() {
                    return "testTransform";
                }

                public Set<QualifiedContent.ContentType> getInputTypes() {
                    return new HashSet<>();
                }

                public Set<? super QualifiedContent.Scope> getScopes() {
                    return new HashSet<>();
                }

                public boolean isIncremental() {
                    return true;
                }

                public void transform(TransformInvocation transformInvocation)
                        throws TransformException, InterruptedException, IOException {
                }
            }

            android {
                registerTransform(new TestTransform())
            }
        """.trimIndent())

        val debugModel =
            project.modelV2().ignoreSyncIssues(severity = IssueReporter.Severity.ERROR.severity)
                .fetchModels("debug")

        val rootBuild = debugModel.container.getProject(":")
        val issues = rootBuild.issues?.syncIssues ?: throw RuntimeException("Missing issues model")
        Truth.assertThat(issues).hasSize(1)
        issues.first().also {
            Truth.assertThat(it.severity).isEqualTo(IssueReporter.Severity.ERROR.severity)
            Truth.assertThat(it.type).isEqualTo(IssueReporter.Type.REMOVED_API.type)
            Truth.assertThat(it.message).contains("API 'android.registerTransform' is removed")
        }

        project.executor().expectFailure().run("assembleDebug").also {
            it.stderr.use { scanner ->
                ScannerSubject.assertThat(scanner).contains(
                    "API 'android.registerTransform' is removed"
                )
            }
        }
    }
}
