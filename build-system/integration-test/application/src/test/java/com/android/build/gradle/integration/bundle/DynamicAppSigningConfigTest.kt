/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.application.SigningTest
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ModelContainerSubject.assertThat
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SyncIssue
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.io.Resources
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

class DynamicAppSigningConfigTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("dynamicApp")
        .withoutNdk()
        .create()

    @Test
    fun testSyncWarning() {
        project.getSubproject("feature1").buildFile.appendText(
                """
                    android {
                        signingConfigs {
                            myConfig {
                                storeFile file("foo.keystore")
                                storePassword "bar"
                                keyAlias "foo"
                                keyPassword "bar"
                            }
                        }
                        buildTypes {
                            debug.signingConfig signingConfigs.myConfig
                        }
                    }
                """.trimIndent())
        val container = project.model().ignoreSyncIssues().fetchAndroidProjects()

        assertThat(container).rootBuild().project(":feature1")
                .hasSingleIssue(
                        EvalIssueReporter.Severity.WARNING.severity,
                        SyncIssue.TYPE_SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE,
                        null,
                    "Signing configuration should not be declared in the "
                            + "dynamic-feature. Dynamic-features use the signing configuration "
                            + "declared in the application module."
                )
    }

    @Test
    fun testNoSyncWarning() {
        val container = project.model().ignoreSyncIssues().fetchAndroidProjects()
        assertThat(container).rootBuild().project(":feature2").hasNoIssues()
    }

    @Test
    fun `assemble with injected signing config`() {
        val keystoreFile = project.file("keystore.jks")
        val keystoreContents =
            Resources.toByteArray(
                Resources.getResource(SigningTest::class.java, "SigningTest/rsa_keystore.jks")
            )
        Files.write(keystoreFile.toPath(), keystoreContents)

        project.executor()
            .with(StringOption.IDE_SIGNING_STORE_FILE, keystoreFile.path)
            .with(StringOption.IDE_SIGNING_STORE_PASSWORD, STORE_PASSWORD)
            .with(StringOption.IDE_SIGNING_KEY_ALIAS, ALIAS_NAME)
            .with(StringOption.IDE_SIGNING_KEY_PASSWORD, KEY_PASSWORD)
            .with(OptionalBooleanOption.SIGNING_V1_ENABLED, true)
            .with(OptionalBooleanOption.SIGNING_V2_ENABLED, true)
            .run("assembleRelease")

        for (subProjectName in listOf("app", "feature1", "feature2")) {
            val subProject = project.getSubproject(subProjectName)

            // Check for signing file inside the APK
            val apk = subProject.getApk(GradleTestProject.ApkType.RELEASE_SIGNED)
            assertThat(apk).contains("META-INF/CERT.RSA")
            assertThat(apk).contains("META-INF/CERT.SF")

            // Check that signing config is not written to disk when passed from the IDE (bug
            // 137210434)
            val signingConfigDir =
                InternalArtifactType.SIGNING_CONFIG.getOutputDir(subProject.buildDir)
            assertThat(signingConfigDir).doesNotExist()
        }
    }
}

private const val STORE_PASSWORD = "store_password"
private const val ALIAS_NAME = "alias_name"
private const val KEY_PASSWORD = "key_password"