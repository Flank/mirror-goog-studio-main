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

import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.dsl.SigningConfigFactory
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.lazyCreate
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.capitalize
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
import org.mockito.junit.MockitoJUnit
import java.io.File
import java.nio.file.Files
import com.android.build.gradle.internal.tasks.factory.lazyCreate

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
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            project = null
        }
    }

    @Rule
    @JvmField
    var mockitoJUnit = MockitoJUnit.rule()!!

    @Mock
    lateinit var variantScope: VariantScope
    @Mock
    lateinit var variantConfiguration : GradleVariantConfiguration

    private lateinit var defaultDebugKeystore: File

    @Before
    fun createDebugKeystoreFile() {
        defaultDebugKeystore = File(temporaryFolder.newFolder(), "debug.keystore")
    }

    fun initPackagingScope(variantName: String) {
        `when`(variantScope.variantConfiguration).thenReturn(variantConfiguration)
        `when`(variantScope.fullVariantName).thenReturn(variantName)
        `when`(variantScope.getTaskName("validateSigning"))
                .thenReturn("validateSigning" + variantName.capitalize())
        `when`(variantScope.getIncrementalDir(anyString())).thenReturn(temporaryFolder.newFolder())
    }

    @Test
    fun testErrorIfNoKeystoreFileSet() {
        initPackagingScope(variantName = "blueRelease")
        `when`(variantConfiguration.signingConfig).thenReturn(SigningConfig("release"))
        val configAction =
                ValidateSigningTask.CreationAction(variantScope, defaultDebugKeystore)
        val task = project!!.tasks.lazyCreate(configAction, null, null, null).get()
        assertThat(task.forceRerun()).named("forceRerun").isTrue()
        // If no config file set, throws InvalidUserDataException
        try {
            task.actions.single().execute(task)
            fail("Expected failure")
        } catch (e: InvalidUserDataException) {
            assertThat(e).hasMessage("Keystore file not set for signing config release")
        }

    }

    @Test
    fun testErrorIfCustomKeystoreFileDoesNotExist() {
        initPackagingScope(variantName = "greenRelease")
        val dslSigningConfig = SigningConfigFactory(project!!.objects,
                temporaryFolder.newFile()).create("release")
        dslSigningConfig.storeFile = File(temporaryFolder.newFolder(), "does_not_exist")
        dslSigningConfig.storePassword = "store password"
        dslSigningConfig.keyAlias = "key alias"
        dslSigningConfig.keyPassword = "key password"
        assertThat(dslSigningConfig.isSigningReady).named("signing is ready").isTrue()
        `when`(variantConfiguration.signingConfig).thenReturn(dslSigningConfig)
        val configAction =
                ValidateSigningTask.CreationAction(variantScope, defaultDebugKeystore)
        val task = project!!.tasks.lazyCreate(configAction, null, null, null).get()
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
        initPackagingScope(variantName = "redDebug")
        val dslSigningConfig =
                SigningConfigFactory(project!!.objects, defaultDebugKeystore).create("debug")
        `when`(variantConfiguration.signingConfig).thenReturn(dslSigningConfig)
        val configAction =
                ValidateSigningTask.CreationAction(variantScope, defaultDebugKeystore)
        val task = project!!.tasks.lazyCreate(configAction, null, null, null).get()

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
