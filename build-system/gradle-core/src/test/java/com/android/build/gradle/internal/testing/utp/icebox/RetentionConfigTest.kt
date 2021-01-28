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

package com.android.build.gradle.internal.testing.utp.icebox

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dsl.FailureRetention
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.testing.utp.createRetentionConfig
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class RetentionConfigTest {
    private lateinit var dslServices: DslServices
    private lateinit var failureRetention: FailureRetention

    val emptyProjectOptions = ProjectOptions(
        ImmutableMap.of(),
        FakeProviderFactory(
            FakeProviderFactory.factory, ImmutableMap.of()
        )
    )

    @Before
    fun setUp() {
        val sdkComponents = Mockito.mock(SdkComponentsBuildService::class.java)
        dslServices = createDslServices(sdkComponents = FakeGradleProvider(sdkComponents))
        failureRetention = FailureRetention(dslServices)
    }

    @Test
    fun disableByDefault() {
        val retentionConfig = createRetentionConfig(emptyProjectOptions, failureRetention)
        assertThat(retentionConfig.enabled).isFalse()
    }

    @Test
    fun enableByDslWithDefaultSetup() {
        failureRetention.enable = true
        val retentionConfig = createRetentionConfig(emptyProjectOptions, failureRetention)
        assertThat(retentionConfig.enabled).isTrue()
        assertThat(retentionConfig.maxSnapshots).isEqualTo(2)
        assertThat(retentionConfig.compressSnapshots).isFalse()
        assertThat(retentionConfig.retainAll).isFalse()
    }

    @Test
    fun setMaxSnapshotsByDsl() {
        val maxSnapshots = 3
        failureRetention.enable = true
        failureRetention.maxSnapshots = maxSnapshots
        val retentionConfig = createRetentionConfig(emptyProjectOptions, failureRetention)
        assertThat(retentionConfig.enabled).isTrue()
        assertThat(retentionConfig.maxSnapshots).isEqualTo(maxSnapshots)
        assertThat(retentionConfig.retainAll).isFalse()
    }

    @Test
    fun setRetainAllByDsl() {
        failureRetention.enable = true
        failureRetention.retainAll()
        val retentionConfig = createRetentionConfig(emptyProjectOptions, failureRetention)
        assertThat(retentionConfig.enabled).isTrue()
        assertThat(retentionConfig.retainAll).isTrue()
    }

    @Test
    fun setCompressionDsl() {
        failureRetention.enable = true
        failureRetention.compressSnapshots = true
        val retentionConfig = createRetentionConfig(emptyProjectOptions, failureRetention)
        assertThat(retentionConfig.enabled).isTrue()
        assertThat(retentionConfig.compressSnapshots).isTrue()
    }

    @Test
    fun overrideCompressionByCommandLine() {
        val properties = ImmutableMap.builder<String, Any>()
        properties.put(
            IntegerOption.TEST_FAILURE_RETENTION.propertyName,
            1
        )
        properties.put(
            OptionalBooleanOption.ENABLE_TEST_FAILURE_RETENTION_COMPRESS_SNAPSHOT.propertyName,
            true
        )
        val projectOptions = ProjectOptions(
            ImmutableMap.of(),
            FakeProviderFactory(
                FakeProviderFactory.factory, properties.build()
            )
        )
        assertThat(failureRetention.enable).isFalse()
        assertThat(failureRetention.compressSnapshots).isFalse()
        val retentionConfig = createRetentionConfig(projectOptions, failureRetention)
        assertThat(retentionConfig.enabled).isTrue()
        assertThat(retentionConfig.compressSnapshots).isTrue()
        assertThat(retentionConfig.maxSnapshots).isEqualTo(1)
    }

    @Test
    fun overrideMaxSnapshotByCommandLine() {
        val maxSnapshot = 3
        val properties = ImmutableMap.builder<String, Any>()
        properties.put(
            IntegerOption.TEST_FAILURE_RETENTION.propertyName,
            maxSnapshot
        )
        val projectOptions = ProjectOptions(
            ImmutableMap.of(),
            FakeProviderFactory(
                FakeProviderFactory.factory, properties.build()
            )
        )
        failureRetention.enable = true
        failureRetention.getRetainAll()
        failureRetention.maxSnapshots = 2
        val retentionConfig = createRetentionConfig(projectOptions, failureRetention)
        assertThat(retentionConfig.enabled).isTrue()
        assertThat(retentionConfig.maxSnapshots).isEqualTo(maxSnapshot)
        assertThat(retentionConfig.retainAll).isFalse()
    }

    @Test
    fun overrideEnableByCommandLine() {
        val properties = ImmutableMap.builder<String, Any>()
        properties.put(
            IntegerOption.TEST_FAILURE_RETENTION.propertyName,
            1
        )
        val projectOptions = ProjectOptions(
            ImmutableMap.of(),
            FakeProviderFactory(
                FakeProviderFactory.factory, properties.build()
            )
        )
        assertThat(failureRetention.enable).isFalse()
        val retentionConfig = createRetentionConfig(projectOptions, failureRetention)
        assertThat(retentionConfig.enabled).isTrue()
    }

    @Test
    fun overrideDisableByCommandLine() {
        val properties = ImmutableMap.builder<String, Any>()
        properties.put(
            IntegerOption.TEST_FAILURE_RETENTION.propertyName,
            0
        )
        val projectOptions = ProjectOptions(
            ImmutableMap.of(),
            FakeProviderFactory(
                FakeProviderFactory.factory, properties.build()
            )
        )
        failureRetention.enable = true
        val retentionConfig = createRetentionConfig(projectOptions, failureRetention)
        assertThat(retentionConfig.enabled).isFalse()
    }

    @Test
    fun overrideRetainAllByCommandLine() {
        val properties = ImmutableMap.builder<String, Any>()
        properties.put(
            IntegerOption.TEST_FAILURE_RETENTION.propertyName,
            -1
        )
        val projectOptions = ProjectOptions(
            ImmutableMap.of(),
            FakeProviderFactory(
                FakeProviderFactory.factory, properties.build()
            )
        )
        failureRetention.enable = true
        assertThat(failureRetention.getRetainAll()).isFalse()
        val retentionConfig = createRetentionConfig(projectOptions, failureRetention)
        assertThat(retentionConfig.enabled).isTrue()
        assertThat(retentionConfig.retainAll).isTrue()
    }
}
