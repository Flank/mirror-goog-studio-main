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

package com.android.projectmodel

import com.android.ide.common.util.PathString
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Test cases for [Config]
 */
class ConfigTest {
    @Test
    fun toStringDefaultTest() {
        val cfg = Config()
        assertThat(cfg.toString()).isEqualTo("Config()")
    }

    @Test
    fun toStringOverrideTest() {
        val cfg = Config(
                applicationIdSuffix = "suffix",
                minifyEnabled = true
        )
        assertThat(cfg.toString()).isEqualTo("Config(applicationIdSuffix=suffix,minifyEnabled=true)")
    }

    @Test
    fun testMerged() {
        val cfg1 = Config(applicationIdSuffix = "Suffix1")
        val cfg2 = Config(applicationIdSuffix = "Suffix2")

        val merged = listOf(cfg1, cfg2).merged()

        assertThat(merged.applicationIdSuffix).isEqualTo("Suffix1Suffix2")
    }

    @Test
    fun testMergedSourceSet() {
        val cfg1 = Config(sources = SourceSet(mapOf(AndroidPathType.JAVA to listOf(PathString("foo")))))
        val cfg2 = Config(sources = SourceSet(mapOf(AndroidPathType.JNI_LIBS to listOf(PathString("bar")))))

        val sourceSet = listOf(cfg1, cfg2).mergedSourceSet()

        assertThat(sourceSet.asMap.keys).containsExactly(AndroidPathType.JAVA, AndroidPathType.JNI_LIBS)
        assertThat(sourceSet[AndroidPathType.JAVA]).containsExactly(PathString("foo"))
        assertThat(sourceSet[AndroidPathType.JNI_LIBS]).containsExactly(PathString("bar"))
    }

    @Test
    fun testWithSources() {
        val sources = SourceSet(mapOf(AndroidPathType.C to listOf(PathString("myFolder"))))
        assertThat(Config(applicationIdSuffix = "foo").withSources(sources))
            .isEqualTo(Config(applicationIdSuffix = "foo", sources = sources))
    }

    @Test
    fun testWithPackageName() {
        assertThat(Config(applicationIdSuffix = "foo").withPackageName("package"))
            .isEqualTo(Config(applicationIdSuffix = "foo", packageName = "package"))
    }

    @Test
    fun testWithManifestValues() {
        val manifestAttributes = ManifestAttributes(applicationId = "foo")
        assertThat(Config().withManifestValues(manifestAttributes)).isEqualTo(Config(manifestValues = manifestAttributes))
    }

    @Test
    fun testWithCompileDeps() {
        val deps = listOf(ArtifactDependency(ExternalLibrary("bar")))
        assertThat(Config().withCompileDeps(deps)).isEqualTo(Config(compileDeps = deps))
    }
}