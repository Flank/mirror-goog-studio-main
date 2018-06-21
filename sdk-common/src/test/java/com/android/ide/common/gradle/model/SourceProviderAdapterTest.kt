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

package com.android.ide.common.gradle.model

import com.android.ide.common.util.PathString
import com.android.projectmodel.AndroidPathType
import com.android.projectmodel.SourceSet
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Tests for [SourceProviderAdapter].
 */
class SourceProviderAdapterTest {
    fun sourceSetWith(type: AndroidPathType, contents: String) =
        SourceSet(mapOf(type to listOf(PathString(contents))))

    fun sourceProviderWith(type: AndroidPathType, contents: String) =
        sourceSetWith(type, contents).toSourceProvider("testProvider")

    @Test
    fun testGetManifestFile() {
        assertThat(
            sourceProviderWith(
                AndroidPathType.MANIFEST,
                "myManifest"
            ).manifestFile
        ).isEqualTo(
            File("myManifest")
        )
    }

    @Test
    fun testGetName() {
        assertThat(sourceProviderWith(AndroidPathType.MANIFEST, "myManifest").name).isEqualTo(
            "testProvider"
        )
    }

    @Test
    fun testGetJavaDirectories() {
        assertThat(
            sourceProviderWith(AndroidPathType.JAVA, "contents").javaDirectories
        ).isEqualTo(
            listOf(File("contents"))
        )
    }

    @Test
    fun testGetAidlDirectories() {
        assertThat(
            sourceProviderWith(AndroidPathType.AIDL, "contents").aidlDirectories
        ).isEqualTo(
            listOf(File("contents"))
        )
    }

    @Test
    fun testGetResourcesDirectories() {
        assertThat(
            sourceProviderWith(AndroidPathType.RESOURCE, "contents").resourcesDirectories
        ).isEqualTo(
            listOf(File("contents"))
        )
    }

    @Test
    fun testGetRenderscriptDirectories() {
        assertThat(
            sourceProviderWith(AndroidPathType.RENDERSCRIPT, "contents").renderscriptDirectories
        ).isEqualTo(
            listOf(File("contents"))
        )
    }

    @Test
    fun testGetCDirectories() {
        assertThat(
            sourceProviderWith(AndroidPathType.C, "contents").cDirectories
        ).isEqualTo(
            listOf(File("contents"))
        )
    }

    @Test
    fun testGetCppDirectories() {
        assertThat(
            sourceProviderWith(AndroidPathType.CPP, "contents").cppDirectories
        ).isEqualTo(
            listOf(File("contents"))
        )
    }

    @Test
    fun testGetAssetsDirectories() {
        assertThat(
            sourceProviderWith(AndroidPathType.ASSETS, "contents").assetsDirectories
        ).isEqualTo(
            listOf(File("contents"))
        )
    }

    @Test
    fun testGetJniLibsDirectories() {
        assertThat(
            sourceProviderWith(AndroidPathType.JNI_LIBS, "contents").jniLibsDirectories
        ).isEqualTo(
            listOf(File("contents"))
        )
    }

    @Test
    fun testResDirectories() {
        assertThat(
            sourceProviderWith(AndroidPathType.RES, "contents").resDirectories
        ).isEqualTo(
            listOf(File("contents"))
        )
    }

    @Test
    fun testShadersDirectories() {
        assertThat(
            sourceProviderWith(AndroidPathType.SHADERS, "contents").shadersDirectories
        ).isEqualTo(
            listOf(File("contents"))
        )
    }

    /**
     * Verifies that if you convert a [SourceSet] to a [SourceProvider] then back, the result is
     * equal to the original.
     */
    @Test
    fun testSuccessfulBidirectionalConversion() {
        val input = SourceSet(
            mapOf(
                AndroidPathType.MANIFEST to listOf("folder/MyManifest.xml"),
                AndroidPathType.JAVA to listOf("java1", "java2"),
                AndroidPathType.AIDL to listOf("aidl1", "aidl2"),
                AndroidPathType.RESOURCE to listOf("resource1", "resource2"),
                AndroidPathType.RENDERSCRIPT to listOf("renderscript1", "renderscript2"),
                AndroidPathType.C to listOf("c1", "c2"),
                AndroidPathType.CPP to listOf("cpp1", "cpp2"),
                AndroidPathType.ASSETS to listOf("assets1", "assets2"),
                AndroidPathType.JNI_LIBS to listOf("jnilibs1", "jnilibs2"),
                AndroidPathType.RES to listOf("res1", "res2"),
                AndroidPathType.SHADERS to listOf("shaders1", "shaders2")
            )
                .mapValues { it.value.map { PathString(it) } }
        )

        val result = input.toSourceProvider("MySourceProvider").toSourceSet()

        assertThat(result).isEqualTo(input)
    }
}