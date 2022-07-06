/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.ide.common.build

import com.google.common.truth.Truth.assertThat
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import junit.framework.TestCase
import java.io.StringReader
import java.io.StringWriter

class GenericBuiltArtifactsTypeAdapterTest : TestCase() {

    private fun prettyPrintJson(writeAction: (jsonWriter: JsonWriter) -> Unit): String {
        return StringWriter().also { stringWriter ->
            JsonWriter(stringWriter).use { jsonWriter ->
                jsonWriter.setIndent("    ")
                writeAction(jsonWriter)
            }
        }.toString()
    }

    private fun <T> TypeAdapter<T>.parseJson(json: String): T =
        JsonReader(StringReader(json)).use { reader -> read(reader).also { assertThat(reader.peek()).isEqualTo(JsonToken.END_DOCUMENT) } }

    private fun <T> verifyRoundTrip(typeAdapter: TypeAdapter<T>, item: T, json: String) {
        assertThat(prettyPrintJson { typeAdapter.write(it, item) }).isEqualTo(json)
        assertThat(typeAdapter.parseJson(json)).named("typeAdapter.read").isEqualTo(item)
    }

    fun testArtifactType() {
        verifyRoundTrip(
            typeAdapter = GenericArtifactTypeTypeAdapter,
            item = GenericArtifactType(type = "APK", kind = "Directory"),
            //language=json
            json = """
                {
                    "type": "APK",
                    "kind": "Directory"
                }
                """.trimIndent()

        )
    }

    fun testGenericBuiltArtifact() {
        verifyRoundTrip(
            typeAdapter = GenericBuiltArtifactTypeAdapter,
            item = GenericBuiltArtifact(
                outputType = "SINGLE",
                filters = listOf(),
                attributes = mapOf(),
                versionCode = 1,
                versionName = "1",
                outputFile = "app-debug.apk"
            ),
            //language=json
            json = """
                {
                    "type": "SINGLE",
                    "filters": [],
                    "attributes": [],
                    "versionCode": 1,
                    "versionName": "1",
                    "outputFile": "app-debug.apk"
                }
                """.trimIndent()

        )
    }

    fun testGenericBuiltArtifacts() {
        verifyRoundTrip(
            typeAdapter = GenericBuiltArtifactsTypeAdapter,
            item = GenericBuiltArtifacts(
                version = 2,
                artifactType = GenericArtifactType("APK", "Directory"),
                applicationId="com.android.test",
                variantName = "debug",
                elements = listOf(
                    GenericBuiltArtifact(
                        outputType = "ONE_OF_MANY",
                        filters = listOf(GenericFilterConfiguration("DENSITY", "xhdpi")),
                        versionCode = 123,
                        versionName = "version_name",
                        outputFile = "file1.apk"
                    ),
                    GenericBuiltArtifact(
                        outputType = "ONE_OF_MANY",
                        filters = listOf(GenericFilterConfiguration("DENSITY", "xhcdpi")),
                        attributes = mapOf("DeliveryType" to "install-time"),
                        versionCode = 123,
                        versionName = "version_name",
                        outputFile = "file2.apk"
                    ),
                ),
                elementType = "File"
                ),
            //language=json
            json = """
                {
                    "version": 2,
                    "artifactType": {
                        "type": "APK",
                        "kind": "Directory"
                    },
                    "applicationId": "com.android.test",
                    "variantName": "debug",
                    "elements": [
                        {
                            "type": "ONE_OF_MANY",
                            "filters": [
                                {
                                    "filterType": "DENSITY",
                                    "value": "xhdpi"
                                }
                            ],
                            "attributes": [],
                            "versionCode": 123,
                            "versionName": "version_name",
                            "outputFile": "file1.apk"
                        },
                        {
                            "type": "ONE_OF_MANY",
                            "filters": [
                                {
                                    "filterType": "DENSITY",
                                    "value": "xhcdpi"
                                }
                            ],
                            "attributes": [
                                {
                                    "key": "DeliveryType",
                                    "value": "install-time"
                                }
                            ],
                            "versionCode": 123,
                            "versionName": "version_name",
                            "outputFile": "file2.apk"
                        }
                    ],
                    "elementType": "File"
                }
                """.trimIndent()
        )
    }


    fun `test parse Android Gradle plugin 4_1 output`() {
        val json = //language=json
            """
                {
                    "version": 2,
                    "artifactType": {
                        "type": "APK",
                        "kind": "Directory"
                    },
                    "applicationId": "com.example.myapplication",
                    "variantName": "processDebugResources",
                    "elements": [
                        {
                            "type": "SINGLE",
                            "filters": [],
                            "versionCode": 1,
                            "versionName": "1.0",
                            "outputFile": "app-debug.apk"
                        }
                    ]
                }
                """.trimIndent()
        assertThat(GenericBuiltArtifactsTypeAdapter.parseJson(json))
            .named("""parseJson("${json.replace("\n", "\\n")}")""")
            .isEqualTo(GenericBuiltArtifacts(
                version = 2,
                artifactType = GenericArtifactType("APK", "Directory"),
                applicationId="com.example.myapplication",
                variantName = "processDebugResources",
                elements = listOf(
                    GenericBuiltArtifact(
                        outputType = "SINGLE",
                        filters = listOf(),
                        versionCode = 1,
                        versionName = "1.0",
                        outputFile = "app-debug.apk"
                    ),
                ),
                elementType = null
            )
        )
    }
}
