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
package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.PackagingOptions
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/** Test for the mutablitly of Packaging Options */
class PackagingOptionsTest {

    private lateinit var packagingOptions: PackagingOptions
    private val dslServices: DslServices = createDslServices()

    interface PackagingOptionsWrapper {
        val packagingOptions: PackagingOptions
    }

    @Before
    fun init() {
        packagingOptions  = androidPluginDslDecorator.decorate(PackagingOptionsWrapper::class.java)
            .getDeclaredConstructor(DslServices::class.java)
            .newInstance(dslServices)
            .packagingOptions
    }

    @Test
    fun `test defaults`() {
        assertThat(packagingOptions.excludes)
            .containsExactly(
                "**/*.kotlin_metadata",
                "**/*~",
                "**/.*",
                "**/.*/**",
                "**/.svn/**",
                "**/CVS/**",
                "**/SCCS/**",
                "**/_*",
                "**/_*/**",
                "**/about.html",
                "**/overview.html",
                "**/package.html",
                "**/picasa.ini",
                "**/protobuf.meta",
                "**/thumbs.db",
                "/LICENSE",
                "/LICENSE.txt",
                "/META-INF/*.DSA",
                "/META-INF/*.EC",
                "/META-INF/*.RSA",
                "/META-INF/*.SF",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/MANIFEST.MF",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/com.android.tools/**",
                "/META-INF/maven/**",
                "/META-INF/proguard/*",
                "/NOTICE",
                "/NOTICE.txt"
            )
        assertThat(packagingOptions.pickFirsts).isEmpty()
        assertThat(packagingOptions.merges)
            .containsExactly(
                "/META-INF/services/**",
                "jacoco-agent.properties",
            )
        assertThat(packagingOptions.doNotStrip).isEmpty()
    }

    @Test
    fun `test excludes mutations are possible`() {
        packagingOptions.excludes.clear()
        assertThat(packagingOptions.excludes).isEmpty()

        packagingOptions.exclude("example1")
        packagingOptions.excludes.add("example2")
        assertThat(packagingOptions.excludes).containsExactly("example1", "example2")

        (packagingOptions as com.android.build.gradle.internal.dsl.PackagingOptions)
            .setExcludes(Sets.union(packagingOptions.excludes, setOf("example3")))
        assertThat(packagingOptions.excludes).containsExactly("example1", "example2", "example3")
    }

    @Test
    fun pickFirsts() {
        packagingOptions.pickFirsts.clear()
        assertThat(packagingOptions.pickFirsts).isEmpty()

        packagingOptions.pickFirst("example1")
        packagingOptions.pickFirsts.add("example2")
        assertThat(packagingOptions.pickFirsts).containsExactly("example1", "example2")

        (packagingOptions as com.android.build.gradle.internal.dsl.PackagingOptions)
            .setPickFirsts(Sets.union(packagingOptions.pickFirsts, setOf("example3")))
        assertThat(packagingOptions.pickFirsts).containsExactly("example1", "example2", "example3");
    }

    @Test
    fun merges() {
        packagingOptions.merges.clear()
        assertThat(packagingOptions.merges).isEmpty()

        packagingOptions.merge("example1")
        packagingOptions.merges.add("example2")
        assertThat(packagingOptions.merges).containsExactly("example1", "example2")

        (packagingOptions as com.android.build.gradle.internal.dsl.PackagingOptions)
            .setMerges(Sets.union(packagingOptions.merges, setOf("example3")))
        assertThat(packagingOptions.merges).containsExactly("example1", "example2", "example3");
    }

    @Test
    fun doNotStrip() {
        packagingOptions.doNotStrip.clear()
        assertThat(packagingOptions.doNotStrip).isEmpty()

        packagingOptions.doNotStrip("example1")
        packagingOptions.doNotStrip.add("example2")
        assertThat(packagingOptions.doNotStrip).containsExactly("example1", "example2")

        (packagingOptions as com.android.build.gradle.internal.dsl.PackagingOptions)
            .setDoNotStrip(Sets.union(packagingOptions.doNotStrip, setOf("example3")))
        assertThat(packagingOptions.doNotStrip).containsExactly("example1", "example2", "example3");
    }
}
