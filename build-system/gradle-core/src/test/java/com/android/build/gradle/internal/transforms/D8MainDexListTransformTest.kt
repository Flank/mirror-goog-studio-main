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

package com.android.build.gradle.internal.transforms

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.testutils.TestInputsGenerator
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier

/**
 * Test for calculating the main dex list using D8.
 */
class D8MainDexListTransformTest {

    @Rule @JvmField
    val tmpDir: TemporaryFolder = TemporaryFolder()

    @Test
    fun testProguardRules() {
        val output = tmpDir.newFile().toPath()

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar, listOf("test/A"))

        val proguardRules = tmpDir.root.toPath().resolve("proguard_rules")
        Files.write(proguardRules, listOf("-keep class test.A"))

        val input = TransformTestHelper.singleJarBuilder(inputJar.toFile())
                .setScopes(QualifiedContent.Scope.PROJECT)
                .build()
        val invocation = TransformTestHelper.invocationBuilder().addReferenceInput(input).build()

        val transform =
                D8MainDexListTransform(
                        manifestProguardRules = proguardRules,
                        outputMainDexList = output,
                        bootClasspath = Supplier { listOf<Path>() })
        transform.transform(invocation)

        Truth.assertThat(Files.readAllLines(output)).containsExactly("test/A.class")
    }

    @Test
    fun testUserProguardRules() {
        val output = tmpDir.newFile().toPath()

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar, listOf("test/A"))

        val userProguardRules = tmpDir.root.toPath().resolve("user_proguard_rules")
        Files.write(userProguardRules, listOf("-keep class test.A"))

        val input = TransformTestHelper.singleJarBuilder(inputJar.toFile())
                .setScopes(QualifiedContent.Scope.PROJECT)
                .build()
        val invocation = TransformTestHelper.invocationBuilder().addReferenceInput(input).build()

        val transform =
                D8MainDexListTransform(
                        manifestProguardRules = tmpDir.newFile().toPath(),
                        userProguardRules = userProguardRules,
                        outputMainDexList = output,
                        bootClasspath = Supplier { listOf<Path>() })
        transform.transform(invocation)

        Truth.assertThat(Files.readAllLines(output)).containsExactly("test/A.class")
    }

    @Test
    fun testAllInputs() {
        val output = tmpDir.newFile().toPath()

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar, listOf("test/A", "test/B"))

        val proguardRules = tmpDir.root.toPath().resolve("proguard_rules")
        Files.write(proguardRules, listOf("-keep class test.A"))

        val userProguardRules = tmpDir.root.toPath().resolve("user_proguard_rules")
        Files.write(userProguardRules, listOf("-keep class test.B"))

        val input = TransformTestHelper.singleJarBuilder(inputJar.toFile())
                .setScopes(QualifiedContent.Scope.PROJECT)
                .build()
        val invocation = TransformTestHelper.invocationBuilder().addReferenceInput(input).build()

        val transform =
                D8MainDexListTransform(
                        manifestProguardRules = proguardRules,
                        userProguardRules = userProguardRules,
                        outputMainDexList = output,
                        bootClasspath = Supplier { listOf<Path>() })
        transform.transform(invocation)

        Truth.assertThat(Files.readAllLines(output))
                .containsExactly("test/A.class", "test/B.class")
    }

    @Test
    fun testUserClassesKeptAndDeDuped() {
        val output = tmpDir.newFile().toPath()

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar, listOf("test/A"))

        val userClasses = tmpDir.root.toPath().resolve("user_rules.txt")
        Files.write(userClasses, listOf("test/User1.class", "test/User2.class", "test/User2.class"))

        val input = TransformTestHelper.singleJarBuilder(inputJar.toFile()).build()
        val invocation = TransformTestHelper.invocationBuilder().addReferenceInput(input).build()

        val transform =
                D8MainDexListTransform(
                        manifestProguardRules = tmpDir.newFile().toPath(),
                        userClasses = userClasses,
                        outputMainDexList = output,
                        bootClasspath = Supplier { listOf<Path>() })
        transform.transform(invocation)

        Truth.assertThat(Files.readAllLines(output))
                .containsExactly("test/User1.class", "test/User2.class")
    }

    @Test
    fun testNoneKept() {
        val output = tmpDir.newFile().toPath()

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar, listOf("test/A"))

        val input = TransformTestHelper.singleJarBuilder(inputJar.toFile()).build()
        val invocation = TransformTestHelper.invocationBuilder().addReferenceInput(input).build()

        val transform =
                D8MainDexListTransform(
                        manifestProguardRules = tmpDir.newFile().toPath(),
                        outputMainDexList = output,
                        bootClasspath = Supplier { listOf<Path>() })
        transform.transform(invocation)

        Truth.assertThat(Files.readAllLines(output)).isEmpty()
    }
}