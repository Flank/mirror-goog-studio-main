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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [ConfigTable]
 */
class ConfigTableTest {
    private val main = Config(applicationIdSuffix = "")
    private val demo = Config(applicationIdSuffix = "demo")
    private val full = Config(applicationIdSuffix = "full")
    private val hires = Config(applicationIdSuffix = "hires")
    private val lowres = Config(applicationIdSuffix = "lowres")
    private val debug = Config(applicationIdSuffix = "debug")
    private val release = Config(applicationIdSuffix = "release")
    private val app = Config(applicationIdSuffix = "app")
    private val tests = Config(applicationIdSuffix = "tests")
    private val fullHires = Config(applicationIdSuffix = "fullHires")

    private val fullHiresReleaseAppPath = matchArtifactsWith("full/hires/release/${ARTIFACT_NAME_MAIN}")

    private val schema = ConfigTableSchema(
            listOf(
                    ConfigDimension("isDemo", listOf("demo", "full")),
                    ConfigDimension("res", listOf("hires", "lowres")),
                    ConfigDimension("buildType", listOf("debug", "release")),
                    ConfigDimension("artifact", listOf(ARTIFACT_NAME_MAIN, ARTIFACT_NAME_UNIT_TEST))
            ))
    val table = ConfigTable(
            schema = schema,
            associations = listOf(
                    ConfigAssociation(matchAllArtifacts(), main),
                    ConfigAssociation(schema.pathFor("demo"), demo),
                    ConfigAssociation(schema.pathFor("full"), full),
                    ConfigAssociation(schema.pathFor("hires"), hires),
                    ConfigAssociation(schema.pathFor("lowres"), lowres),
                    ConfigAssociation(schema.pathFor("full").intersect(schema.pathFor("hires")),
                            fullHires),
                    ConfigAssociation(schema.pathFor("debug"), debug),
                    ConfigAssociation(schema.pathFor("release"), release),
                    ConfigAssociation(schema.pathFor(ARTIFACT_NAME_MAIN), app),
                    ConfigAssociation(schema.pathFor(ARTIFACT_NAME_UNIT_TEST), tests)
            )
    )

    @Test
    fun testGenerateVariants() {
        val variants = table.generateVariants()

        val demoLowresDebug = variants.find {it.name == "demoLowresDebug"}!!
        assertThat(variants.size).isEqualTo(8)
        assertThat(demoLowresDebug.mainArtifact.name).isEqualTo(ARTIFACT_NAME_MAIN)
        assertThat(demoLowresDebug.mainArtifact.resolved.applicationIdSuffix).isEqualTo("demolowresdebugapp")
    }

    @Test
    fun testConfigs() {
        assertThat(table.configs).isEqualTo(listOf(
                main,
                demo,
                full,
                hires,
                lowres,
                fullHires,
                debug,
                release,
                app,
                tests))
    }

    @Test
    fun testConfigsContaining() {
        // Locate all configs common to all artifacts in the fullHiresReleaseApp variant
        assertThat(table.filter { it.path.contains(fullHiresReleaseAppPath) }.configs).isEqualTo(listOf(
                main,
                full,
                hires,
                fullHires,
                release,
                app))
        // Locate configs common to all artifacts in the full flavor
        assertThat(table.filter { it.path.contains(schema.pathFor("full")) }.configs).isEqualTo(listOf(main, full))
    }

    @Test
    fun testConfigsIntersecting() {
        // Locate all configs used by any artifact in the fullHiresReleaseApp variant
        assertThat(table.configsIntersecting(fullHiresReleaseAppPath)).isEqualTo(listOf(
                main,
                full,
                hires,
                fullHires,
                release,
                app))
        // Locate configs used by any artifact in the "full" flavor
        assertThat(table.configsIntersecting(schema.pathFor("full"))).isEqualTo(listOf(
                main,
                full,
                hires,
                lowres,
                fullHires,
                debug,
                release,
                app,
                tests))
        // Locate configs used by any artifact in the "demo" flavor
        assertThat(table.configsIntersecting(schema.pathFor("demo"))).isEqualTo(listOf(
                main,
                demo,
                hires,
                lowres,
                debug,
                release,
                app,
                tests))
    }

    @Test
    fun testConfigsNotContaining() {
        // Locate all configs that aren't common to all artifacts in the fullHiresReleaseApp variant
        assertThat(table.filter { !it.path.contains(fullHiresReleaseAppPath) }.configs).isEqualTo(listOf(
                demo,
                lowres,
                debug,
                tests))
        // Locate all configs that aren't common to all artifacts in the full flavor
        assertThat(table.filter { !it.path.contains(schema.pathFor("full")) }.configs).isEqualTo(listOf(
                demo,
                hires,
                lowres,
                fullHires,
                debug,
                release,
                app,
                tests))
    }

    @Test
    fun testConfigsNotIntersecting() {
        // Locate all configs used by any artifact in the fullHiresReleaseApp variant
        assertThat(table.configsNotIntersecting(fullHiresReleaseAppPath)).isEqualTo(listOf(
                demo,
                lowres,
                debug,
                tests))
        // Locate configs used by any artifact in the "full" flavor
        assertThat(table.configsNotIntersecting(schema.pathFor("full"))).isEqualTo(listOf(demo))
        // Locate configs used by any artifact in the "demo" flavor
        assertThat(table.configsNotIntersecting(schema.pathFor("demo"))).isEqualTo(listOf(
                full,
                fullHires))
    }

    @Test
    fun testFilterContaining() {
        // Locate configs that are used by all variants of the "tests" artifact.
        val filtered = table.filter { it.path.contains(matchArtifactsWith("*/*/*/${ARTIFACT_NAME_UNIT_TEST}")) }
        assertThat(filtered.schema).isEqualTo(table.schema)
        assertThat(filtered.configs).isEqualTo(listOf(main, tests))
        assertThat(filtered.associations).isEqualTo(listOf(
                ConfigAssociation(matchAllArtifacts(), main),
                ConfigAssociation(schema.pathFor("${ARTIFACT_NAME_UNIT_TEST}"), tests)))
    }

    @Test
    fun testFilterNotContaining() {
        // Locate all configs that aren't common to the app artifact in the demo/lowres flavor
        // across all build types.
        val filtered = table.filter { !it.path.contains(matchArtifactsWith("demo/lowres/*/${ARTIFACT_NAME_MAIN}")) }
        assertThat(filtered.schema).isEqualTo(table.schema)
        assertThat(filtered.configs).isEqualTo(listOf(
                full,
                hires,
                fullHires,
                debug,
                release,
                tests))
        assertThat(filtered.associations).isEqualTo(listOf(
                ConfigAssociation(matchArtifactsWith("full"), full),
                ConfigAssociation(matchArtifactsWith("*/hires"), hires),
                ConfigAssociation(matchArtifactsWith("full/hires"), fullHires),
                ConfigAssociation(matchArtifactsWith("*/*/debug"), debug),
                ConfigAssociation(matchArtifactsWith("*/*/release"), release),
                ConfigAssociation(matchArtifactsWith("*/*/*/${ARTIFACT_NAME_UNIT_TEST}"), tests)
        ))
    }

    @Test
    fun testFilterIntersecting() {
        // Locate all configs used by any variant of the hires app artifact
        val filtered = table.filterIntersecting(matchArtifactsWith("*/hires/*/${ARTIFACT_NAME_MAIN}"))
        assertThat(filtered.schema).isEqualTo(table.schema)
        assertThat(filtered.configs).isEqualTo(listOf(main, demo, full, hires, fullHires, debug, release, app))
        assertThat(filtered.associations).isEqualTo(listOf(
                ConfigAssociation(matchAllArtifacts(), main),
                ConfigAssociation(matchArtifactsWith("demo"), demo),
                ConfigAssociation(matchArtifactsWith("full"), full),
                ConfigAssociation(matchArtifactsWith("*/hires"), hires),
                ConfigAssociation(matchArtifactsWith("full/hires"), fullHires),
                ConfigAssociation(matchArtifactsWith("*/*/debug"), debug),
                ConfigAssociation(matchArtifactsWith("*/*/release"), release),
                ConfigAssociation(matchArtifactsWith("*/*/*/${ARTIFACT_NAME_MAIN}"), app)
        ))
    }

    @Test
    fun testFilterNotIntersecting() {
        // Locate all configs that aren't used by any variant of the hires app artifact
        val filtered = table.filterNotIntersecting(matchArtifactsWith("*/hires/*/${ARTIFACT_NAME_MAIN}"))
        assertThat(filtered.schema).isEqualTo(table.schema)
        assertThat(filtered.configs).isEqualTo(listOf(lowres, tests))
        assertThat(filtered.associations).isEqualTo(listOf(
                ConfigAssociation(matchArtifactsWith("*/lowres"), lowres),
                ConfigAssociation(matchArtifactsWith("*/*/*/${ARTIFACT_NAME_UNIT_TEST}"), tests)
        ))
    }

    @Test
    fun testSingleElementConfigTableConstructor() {
        val config = Config(versionNameSuffix = "foo")
        val actual = configTableWith(config)
        val expected = configTableWith(
            ConfigTableSchema(
                listOf(
                    ConfigDimension(
                        ARTIFACT_DIMENSION_NAME,
                        listOf(ARTIFACT_NAME_MAIN)
                    )
                )
            ),
            mapOf(ARTIFACT_NAME_MAIN to config)
        )
        assertThat(actual).isEqualTo(expected)
    }

    /**
     * Tests the [ConfigTableSchema.buildTable] factory method that builds [ConfigTable] instances
     * given a schema and a list of [Config] instances along with their simple path names.
     */
    @Test
    fun testBuildTable() {
        val actual = schema.buildTable(
            null to main,
            "full" to full,
            "hires" to hires,
            "lowres" to lowres,
            ARTIFACT_NAME_MAIN to app
        )

        val expected = ConfigTable(
            schema, listOf(
                ConfigAssociation(matchAllArtifacts(), main),
                ConfigAssociation(schema.pathFor("full"), full),
                ConfigAssociation(schema.pathFor("hires"), hires),
                ConfigAssociation(schema.pathFor("lowres"), lowres),
                ConfigAssociation(schema.pathFor(ARTIFACT_NAME_MAIN), app)
            )
        )

        assertThat(actual).isEqualTo(expected)
    }

    /**
     * Tests the [configTableWith] factory method that builds [ConfigTable] instances
     * given a schema and a map list of [Config] instances along with their simple path names.
     */
    @Test
    fun testConfigTableWith() {
        val actual = configTableWith(
            schema, mapOf(
                null to main,
                "full" to full,
                "hires" to hires,
                "lowres" to lowres,
                ARTIFACT_NAME_MAIN to app
            )
        )

        val expected = ConfigTable(
            schema, listOf(
                ConfigAssociation(matchAllArtifacts(), main),
                ConfigAssociation(schema.pathFor("full"), full),
                ConfigAssociation(schema.pathFor("hires"), hires),
                ConfigAssociation(schema.pathFor("lowres"), lowres),
                ConfigAssociation(schema.pathFor(ARTIFACT_NAME_MAIN), app)
            )
        )

        assertThat(actual).isEqualTo(expected)
    }
}
