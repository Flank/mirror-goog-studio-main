/*
 * Copyright (C) 2019 The Android Open Source Project
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
@file:JvmName("TestAarGenerator")

package com.android.testutils

import com.android.testutils.TestInputsGenerator.jarWithEmptyEntries
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Returns the bytes of an AAR with the given content.
 *
 * Designed for use with [TestInputsGenerator] to generate jars to put in the AAR and with
 * [MavenRepoGenerator] to put the AAR in a test maven repo.
 *
 * @param packageName The package name to put in the AAR manifest.
 * @param mainJar the contents of the main classes.jar. Defaults to an empty jar.
 * @param secondaryJars a map of name (without extension) to contents of jars
 *                      to be stored as `libs/{name}.jar`. Defaults to empty.
 */
@JvmOverloads
fun generateAarWithContent(
    packageName: String,
    mainJar: ByteArray = jarWithEmptyEntries(listOf()),
    secondaryJars: Map<String, ByteArray> = mapOf(),
    resources: Map<String, ByteArray> = mapOf(),
    apiJar: ByteArray? = null
): ByteArray {
    ByteArrayOutputStream().use { baos ->
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write("""<manifest package="$packageName"></manifest>""".toByteArray(Charsets.UTF_8))
            zos.putNextEntry(ZipEntry("classes.jar"))
            zos.write(mainJar)
            secondaryJars.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry("libs/$name.jar"))
                zos.write(content)
            }
            resources.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry("res/$name"))
                zos.write(content)
            }
            apiJar?.let {
                zos.putNextEntry(ZipEntry("api.jar"))
                zos.write(it)
            }
        }
        return baos.toByteArray()
    }
}
