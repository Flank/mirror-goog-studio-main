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
    val packageName: String?
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
        addFile(TestSourceFile("build.gradle", buildScript))

        if (addManifestFile) {
            checkNotNull(packageName) { "packageName must be provided when addManifestFile=true" }
            val manifest = """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                         xmlns:dist="http://schemas.android.com/apk/distribution"
                    package="$packageName">
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

    fun appendToBuild(snippet: String): MinimalSubProject {
        replaceFile(getFile("build.gradle").appendContent("\n" + snippet + "\n"))
        return this
    }

    companion object {

        fun app(packageName: String): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.application",
                addCompileAndSdkVersionToBuildFile = true,
                addVersionCodeToBuildFile = true,
                addManifestFile = true,
                packageName = packageName
            )
        }

        fun lib(packageName: String): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.library",
                addCompileAndSdkVersionToBuildFile = true,
                addVersionCodeToBuildFile = false,
                addManifestFile = true,
                packageName = packageName
            )
        }

        fun feature(packageName: String): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.feature",
                addCompileAndSdkVersionToBuildFile = true,
                addVersionCodeToBuildFile = false,
                addManifestFile = true,
                packageName = packageName
            )
        }

        fun dynamicFeature(packageName: String): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.dynamic-feature",
                addCompileAndSdkVersionToBuildFile = true,
                addVersionCodeToBuildFile = false,
                addManifestFile = true,
                packageName = packageName
            )
        }

        fun test(packageName: String): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.test",
                addCompileAndSdkVersionToBuildFile = true,
                addVersionCodeToBuildFile = false,
                addManifestFile = true,
                packageName = packageName
            )
        }

        fun javaLibrary(): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "java-library",
                addCompileAndSdkVersionToBuildFile = false,
                addVersionCodeToBuildFile = false,
                addManifestFile = false,
                packageName = null
            )
        }

        fun assetPack(): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.asset-pack",
                addCompileAndSdkVersionToBuildFile = false,
                addVersionCodeToBuildFile = false,
                addManifestFile = false,
                packageName = null
            )
        }

        fun assetPackBundle(): MinimalSubProject {
            return MinimalSubProject(
                path = null,
                plugin = "com.android.asset-pack-bundle",
                addCompileAndSdkVersionToBuildFile = false,
                addVersionCodeToBuildFile = false,
                addManifestFile = false,
                packageName = null
            )
        }
    }
}
