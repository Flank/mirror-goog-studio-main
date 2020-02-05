package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.tasks.DependenciesUsageReport
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.generateAarWithContent
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [AnalyzeDependenciesTask]
 */
class AnalyzeDependenciesTest {

    private val app = MinimalSubProject.app("com.example.app")
            .appendToBuild(
                    """
                dependencies {
                implementation 'com.analyzedependenciesTest:aar:1'
                 }
            """.trimIndent()
            )

    private val emptyAar = generateAarWithContent("com.analyzedependenciesTest.aar")


    private val mavenRepo = MavenRepoGenerator(
            listOf(
                    MavenRepoGenerator.Library(
                            "com.analyzedependenciesTest:aar:1", "aar", emptyAar)
            )
    )

    private val testApp =
            MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .build()

    @get:Rule
    val project = GradleTestProject.builder()
            .withAdditionalMavenRepo(mavenRepo)
            .fromTestApp(testApp)
            .create()

    @Test
    fun `Verify correct dependencies report is produced, only considering class references`() {
        project.execute(":app:assembleRelease")
        project.execute(":app:analyzeReleaseDependencies")

        val dependencyAnalysisReport = project.getSubproject(":app").getIntermediateFile(
                "analyze_dependencies_report",
                "release",
                "analyzeDependencies",
                "dependenciesReport.json"
        )
        assertThat(dependencyAnalysisReport.exists())

        val dependencyReportJson = dependencyAnalysisReport.readText()
        val parsedJson =
                Gson().fromJson(dependencyReportJson, DependenciesUsageReport::class.java)

        assertThat(parsedJson.add.size).isEqualTo(0)
        assertThat(parsedJson.remove.size).isEqualTo(1)
        assertThat(parsedJson.remove).contains("com.analyzedependenciesTest:aar:1")
    }
}