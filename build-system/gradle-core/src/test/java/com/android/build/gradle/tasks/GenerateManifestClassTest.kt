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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.generators.ManifestClassData
import com.android.build.gradle.internal.generators.ManifestClassGenerator
import com.android.build.gradle.internal.generators.getPermissionName
import com.android.ide.common.symbols.parseManifest
import com.android.testutils.apk.Zip
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Field
import java.net.URLClassLoader

private const val PERMISSION_ONE = "com.permission.ONE"
private const val PERMISSION_TWO = "com.permission.TWO"
private const val CLASHING_PERMISSION = "com.clashing.ONE"

class GenerateManifestClassTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun testSimplePermission() {
        val manifestTestConfig = ManifestClassData(
                manifestFile = createManifest(listOf(PERMISSION_ONE)),
                namespace = "test",
                outputFilePath = temporaryFolder.newFile("manifest.jar"))

        ManifestClassGenerator(manifestTestConfig).generate()

        Zip(manifestTestConfig.outputFilePath).use {
            assertThat(it.entries).hasSize(2)
            assertThat(it.entries.map { f -> f.toString() })
                    .containsExactly("/test/Manifest.class", "/test/Manifest\$permission.class")
        }

        URLClassLoader(arrayOf(manifestTestConfig.outputFilePath.toURI().toURL()), null).use {
            val actualFields = loadFields(it, "test.Manifest\$permission")
            assertThat(actualFields)
                    .containsExactly("""java.lang.String ONE = "$PERMISSION_ONE"""")
        }
    }

    @Test
    fun testClashingPermission() {
        val manifestClashingPermission = ManifestClassData(
                manifestFile = createManifest(listOf(PERMISSION_ONE, CLASHING_PERMISSION)),
                namespace = "test",
                outputFilePath = temporaryFolder.newFile("manifest.jar")
        )

        ManifestClassGenerator(manifestClashingPermission).generate()

        URLClassLoader(arrayOf(manifestClashingPermission.outputFilePath.toURI().toURL()), null).use {
            val actualFields = loadFields(it, "test.Manifest\$permission")
            // The last one wins.
            assertThat(actualFields)
                    .containsExactly("""java.lang.String ONE = "$CLASHING_PERMISSION"""")
        }

        // Now swap order and make sure the last one wins.
        FileUtils.delete(manifestClashingPermission.manifestFile)
        val manifestPermissionOne = manifestClashingPermission.copy(
                manifestFile = createManifest(listOf(CLASHING_PERMISSION, PERMISSION_ONE)))
        ManifestClassGenerator(manifestPermissionOne).generate()

        URLClassLoader(arrayOf(manifestPermissionOne.outputFilePath.toURI().toURL()), null).use {
            val actualFields = loadFields(it, "test.Manifest\$permission")
            assertThat(actualFields)
                    .containsExactly("""java.lang.String ONE = "$PERMISSION_ONE"""")
        }
    }

    @Test
    fun testAllPermissions() {
        val manifestTestConfig = ManifestClassData(
                manifestFile = createManifest(listOf(PERMISSION_ONE, PERMISSION_TWO, CLASHING_PERMISSION)),
                namespace = "com.example.app",
                outputFilePath = temporaryFolder.newFile("manifest.jar")
        )
        ManifestClassGenerator(manifestTestConfig).generate()

        URLClassLoader(arrayOf(manifestTestConfig.outputFilePath.toURI().toURL()), null).use {
            val actualFields = loadFields(it, "com.example.app.Manifest\$permission")
            // The last one wins.
            assertThat(actualFields)
                    .containsExactly(
                            """java.lang.String ONE = "$CLASHING_PERMISSION"""",
                            """java.lang.String TWO = "$PERMISSION_TWO"""")
        }
    }

    @Test
    fun testNoPermissionsEmptyClass() {
        val manifestTestConfig = ManifestClassData(
                manifestFile = createManifest(listOf()),
                namespace = "test",
                outputFilePath = temporaryFolder.newFile("manifest.jar")
        )

        ManifestClassGenerator(manifestTestConfig).generate()

        URLClassLoader(arrayOf(manifestTestConfig.outputFilePath.toURI().toURL()), null).use {
            val actualFields = loadFields(it, "test.Manifest")
            assertThat(actualFields).isEmpty()
        }
    }

    @Test
    fun testGetFullyQualifiedClassName() {
        val manifestTestConfig = ManifestClassData(
                manifestFile = createManifest(listOf()),
                namespace = "",
                outputFilePath = temporaryFolder.newFile("manifest.jar")
        )
        var manifestClassGenerator = ManifestClassGenerator(manifestTestConfig)
        assertThat(manifestClassGenerator.fullyQualifiedManifestClassName).isEqualTo("Manifest")

        manifestClassGenerator = ManifestClassGenerator(
                manifestTestConfig.copy(namespace = "test"))
        assertThat(manifestClassGenerator.fullyQualifiedManifestClassName)
                .isEqualTo("test/Manifest")

        manifestClassGenerator = ManifestClassGenerator(
                manifestTestConfig.copy(namespace = "com.example.app"))
        assertThat(manifestClassGenerator.fullyQualifiedManifestClassName)
                .isEqualTo("com/example/app/Manifest")
    }

    @Test
    fun testGetPermissionName() {
        assertThat(getPermissionName(PERMISSION_ONE)).isEqualTo("ONE")
        assertThat(getPermissionName(CLASHING_PERMISSION)).isEqualTo("ONE")
        assertThat(getPermissionName(PERMISSION_ONE))
                .isEqualTo(getPermissionName(CLASHING_PERMISSION))
    }

    @Test
    fun testParsingManifestForCustomPermissions() {
        val manifest = createManifest(listOf(PERMISSION_ONE, PERMISSION_TWO, CLASHING_PERMISSION))
        val foundPermissions = parseManifest(manifest).customPermissions

        assertThat(foundPermissions).hasSize(3)
        assertThat(foundPermissions)
                .containsExactly(PERMISSION_ONE, PERMISSION_TWO, CLASHING_PERMISSION)
    }

    private fun createManifest(permissions: List<String>): File {
        val manifest = temporaryFolder.newFile("AndroidManifest.xml")
        var content =
                """
                <manifest
                  xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example.app" >
             """

        permissions.forEach {
            content +=
                    """
                <permission
                android:name = "$it"
                android:label = "@string/deadlyActivity"
                android:description = "@string/deadlyActivity"
                android:permissionGroup = "android.permission-group.COST_MONEY"
                android:protectionLevel = "dangerous" />
                """
        }

        content += """</manifest>"""

        FileUtils.writeToFile(manifest, content.trimIndent())
        return manifest
    }

    private fun loadFields(classLoader: ClassLoader, name: String) =
            classLoader.loadClass(name)
                    .fields
                    .map { field ->
                        "${field.type.typeName} ${field.name} = ${valueAsString(field)}"
                    }
                    .toList()

    private fun valueAsString(field: Field) = when (field.type.typeName) {
        "java.lang.String" -> "\"${field.get(null)}\""
        else -> throw IllegalStateException("Unexpected type " + field.type.typeName)
    }
}
