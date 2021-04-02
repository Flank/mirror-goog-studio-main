/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.api.variant.impl.SigningConfigImpl
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.builder.signing.DefaultSigningConfig
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.hash.Hashing
import com.google.common.truth.Truth.assertThat
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.AfterClass
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File
import java.nio.file.Files
import java.security.KeyStore

const val PRE_BUILD_TASKNAME = "preBuildTask"

class ValidateSigningTaskTest {

    companion object {
        @ClassRule
        @JvmField
        var temporaryFolder = TemporaryFolder()
        private var project: Project? = null

        @BeforeClass
        @JvmStatic
        fun createProject() {
            project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build()
                .also { project ->
                    project.tasks.register(PRE_BUILD_TASKNAME)
                }
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            project = null
        }
    }

    lateinit var outputDirectory : File
    private lateinit var defaultDebugKeystore: File

    @Before
    fun createDebugKeystoreFile() {
        defaultDebugKeystore = File(temporaryFolder.newFolder(), "debug.keystore")
        outputDirectory = temporaryFolder.newFolder()
    }

    @Test
    fun testErrorIfNoKeystoreFileSet() {
        val task= project!!.tasks.create("validateSigning", ValidateSigningTask::class.java)
        task.dummyOutputDirectory.set(outputDirectory)
        task.analyticsService.set(FakeNoOpAnalyticsService())

        val signingConfig = Mockito.mock(SigningConfigImpl::class.java)
        Mockito.`when`(signingConfig.name).thenReturn("release")
        Mockito.`when`(signingConfig.storeFile).thenReturn(FakeGradleProvider(null))
        Mockito.`when`(signingConfig.storePassword).thenReturn(FakeGradleProvider("store password"))
        Mockito.`when`(signingConfig.keyAlias).thenReturn(FakeGradleProvider("key alias"))
        Mockito.`when`(signingConfig.keyPassword).thenReturn(FakeGradleProvider("key password"))
        Mockito.`when`(signingConfig.storeType).thenReturn(FakeGradleProvider(null))
        task.signingConfigData.set(SigningConfigData.fromSigningConfig(signingConfig))

        assertThat(task.forceRerun()).named("forceRerun").isTrue()
        // If no config file set, throws InvalidUserDataException
        try {
            task.actions.single().execute(task)
            fail("Expected failure")
        } catch (e: InvalidUserDataException) {
            assertThat(e).hasMessageThat().isEqualTo("Keystore file not set for signing config release")
        }

    }

    @Test
    fun testErrorIfCustomKeystoreFileDoesNotExist() {
        val task = project!!.tasks.create("validateGreenSigning", ValidateSigningTask::class.java)

        val signingConfig = Mockito.mock(SigningConfigImpl::class.java)
        Mockito.`when`(signingConfig.name).thenReturn("release")
        Mockito.`when`(signingConfig.storeFile).thenReturn(FakeGradleProvider(
            File(temporaryFolder.newFolder(), "does_not_exist")))
        Mockito.`when`(signingConfig.storePassword).thenReturn(FakeGradleProvider("store password"))
        Mockito.`when`(signingConfig.keyAlias).thenReturn(FakeGradleProvider("key alias"))
        Mockito.`when`(signingConfig.keyPassword).thenReturn(FakeGradleProvider("key password"))
        Mockito.`when`(signingConfig.storeType).thenReturn(FakeGradleProvider(null))
        task.signingConfigData.set(SigningConfigData.fromSigningConfig(signingConfig))

        task.dummyOutputDirectory.set(outputDirectory)
        task.analyticsService.set(FakeNoOpAnalyticsService())

        assertThat(task.forceRerun()).named("forceRerun").isTrue()
        // If no config file set, throws InvalidUserDataException
        try {
            task.actions.single().execute(task)
            fail("Expected failure")
        } catch (e: InvalidUserDataException) {
            assertThat(e.message)
                    .matches("^Keystore file .* not found for signing config 'release'.$")
        }
    }

    @Test
    fun testDefaultDebugKeystoreIsCreatedAutomatically() {
        val task = project!!.tasks.create("validateRedSigning", ValidateSigningTask::class.java)
        val signingConfig = Mockito.mock(SigningConfigImpl::class.java)
        Mockito.`when`(signingConfig.name).thenReturn("debug")
        Mockito.`when`(signingConfig.storeFile).thenReturn(FakeGradleProvider(defaultDebugKeystore))
        Mockito.`when`(signingConfig.storePassword).thenReturn(
            FakeGradleProvider(DefaultSigningConfig.DEFAULT_PASSWORD)
        )
        Mockito.`when`(signingConfig.keyAlias).thenReturn(
            FakeGradleProvider(DefaultSigningConfig.DEFAULT_ALIAS)
        )
        Mockito.`when`(signingConfig.keyPassword).thenReturn(
            FakeGradleProvider(DefaultSigningConfig.DEFAULT_PASSWORD)
        )
        Mockito.`when`(signingConfig.storeType).thenReturn(
            FakeGradleProvider(KeyStore.getDefaultType())
        )
        Mockito.`when`(signingConfig.isSigningReady()).thenReturn(true)

        task.signingConfigData.set(SigningConfigData.fromSigningConfig(signingConfig))
        task.dummyOutputDirectory.set(outputDirectory)
        task.analyticsService.set(FakeNoOpAnalyticsService())
        task.defaultDebugKeystoreLocation.set(defaultDebugKeystore)

        // Sanity check
        assertThat(defaultDebugKeystore).doesNotExist()
        assertThat(task.forceRerun()).named("forceRerun").isTrue()

        // Run task action to generate debug keystore.
        task.actions.single().execute(task)

        // Check the keystore is created
        assertThat(defaultDebugKeystore.toPath()).isFile()
        assertThat(task.forceRerun()).named("forceRerun").isFalse()
        // Check that re-run does not rewrite the keystore. The keystore contains cryptographic
        // keys, so it will be different each time it is generated.
        val debugKeystoreHash =
                Hashing.sha512().hashBytes(Files.readAllBytes(defaultDebugKeystore.toPath()))
        task.actions.single().execute(task)
        assertThat(Hashing.sha512().hashBytes(Files.readAllBytes(defaultDebugKeystore.toPath())))
                .isEqualTo(debugKeystoreHash)
        assertThat(task.forceRerun()).named("forceRerun").isFalse()

        // Check that a new, different keystore can be created if it is deleted.
        Files.delete(defaultDebugKeystore.toPath())
        assertThat(task.forceRerun()).named("forceRerun").isTrue()
        task.actions.single().execute(task)
        assertThat(Hashing.sha512().hashBytes(Files.readAllBytes(defaultDebugKeystore.toPath())))
                .isNotEqualTo(debugKeystoreHash)
    }
}
