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

package com.android.build.gradle.integration.common.fixture.app

import com.android.build.gradle.integration.common.fixture.BuildSrcProject
import com.android.build.gradle.integration.common.fixture.GradleProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_MIN_SDK

/** A subproject with minimal contents. */
class MinimalSubProject private constructor(

    /**
     * Logical path to this project (e.g., ":app"). If it is provided and doesn't start with ':', it
     * will be normalized to start with ':'.
     */
    path: String? = null,

    val plugin: String,
    val addCompileAndSdkVersionToBuildFile: Boolean = false,
    val addVersionCodeToBuildFile: Boolean = false,
    val addManifestFile: Boolean = false,
    val namespace: String?
) :
    GradleProject(path) {

    init {
        var buildScript = "apply plugin: '$plugin'\n"
        if (addCompileAndSdkVersionToBuildFile) {
            buildScript += "\nandroid.compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}" +
                    "\nandroid.defaultConfig.minSdkVersion $SUPPORT_LIB_MIN_SDK\n"
        }
        if (addVersionCodeToBuildFile) {
            buildScript += "\nandroid.defaultConfig.versionCode 1\n"
        }
        namespace?.let { buildScript += "\nandroid.namespace \"$it\"\n"}
        addFile(TestSourceFile("build.gradle", buildScript))

        if (addManifestFile) {
            val manifest = """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                         xmlns:dist="http://schemas.android.com/apk/distribution">
                    <application />
                </manifest>""".trimMargin()
            addFile(TestSourceFile("src/main/AndroidManifest.xml", manifest))
        }
    }

    override fun containsFullBuildScript(): Boolean {
        return false
    }

    fun withFile(relativePath: String, content: ByteArray): MinimalSubProject {
        replaceFile(TestSourceFile(relativePath, content))
        return this
    }

    fun withFile(relativePath: String, content: String): MinimalSubProject {
        replaceFile(TestSourceFile(relativePath, content))
        return this
    }

    override fun appendToBuild(snippet: String): MinimalSubProject {
        return super.appendToBuild(snippet) as MinimalSubProject
    }

    companion object {

        fun buildSrc(): BuildSrcProject {
            return BuildSrcProject()
        }

        @JvmOverloads
        fun app(namespace: String = "com.example.app", projectPath: String = "app"): MinimalSubProject {
            return MinimalSubProject(
                path = projectPath,
                plugin = "com.android.application",
                addCompileAndSdkVersionToBuildFile = true,
                addVersionCodeToBuildFile = true,
                addManifestFile = true,
                namespace = namespace
            )
        }

        @JvmOverloads
        fun lib(namespace: String = "com.example.lib", projectPath: String = "lib"): MinimalSubProject {
            return MinimalSubProject(
                path = projectPath,
                plugin = "com.android.library",
                addCompileAndSdkVersionToBuildFile = true,
                addVersionCodeToBuildFile = false,
                addManifestFile = true,
                namespace = namespace
            )
        }

        fun feature(namespace: String): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.feature",
                addCompileAndSdkVersionToBuildFile = true,
                addVersionCodeToBuildFile = false,
                addManifestFile = true,
                namespace = namespace
            )
        }

        fun dynamicFeature(namespace: String): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.dynamic-feature",
                addCompileAndSdkVersionToBuildFile = true,
                addVersionCodeToBuildFile = false,
                addManifestFile = true,
                namespace = namespace
            )
        }

        fun test(namespace: String): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.test",
                addCompileAndSdkVersionToBuildFile = true,
                addVersionCodeToBuildFile = false,
                addManifestFile = true,
                namespace = namespace
            )
        }

        fun javaLibrary(): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "java-library",
                addCompileAndSdkVersionToBuildFile = false,
                addVersionCodeToBuildFile = false,
                addManifestFile = false,
                namespace = null
            )
        }

        fun assetPack(): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.asset-pack",
                addCompileAndSdkVersionToBuildFile = false,
                addVersionCodeToBuildFile = false,
                addManifestFile = false,
                namespace = null
            )
        }

        fun assetPackBundle(): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.asset-pack-bundle",
                addCompileAndSdkVersionToBuildFile = false,
                addVersionCodeToBuildFile = false,
                addManifestFile = false,
                namespace = null
            )
        }

        fun fusedLibrary(namespace: String): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.fused-library",
                addCompileAndSdkVersionToBuildFile = false,
                addVersionCodeToBuildFile = false,
                addManifestFile = false,
                namespace = namespace
            )
        }

        fun privacySandboxSdk(namespace: String): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.privacy-sandbox-sdk",
                addCompileAndSdkVersionToBuildFile = false,
                addVersionCodeToBuildFile = false,
                addManifestFile = false,
                namespace = namespace
            )
        }
    }
}
