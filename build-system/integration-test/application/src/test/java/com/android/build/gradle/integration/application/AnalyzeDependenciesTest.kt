package com.android.build.gradle.integration.application

import android.databinding.tool.ext.toCamelCase
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.tasks.DependenciesUsageReport
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.generateAarWithContent
import com.google.common.io.Resources
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [AnalyzeDependenciesTask]
 */
class AnalyzeDependenciesTest {

    private val emptyAar = generateAarWithContent("com.analyzedependenciesTest.emptyaar")
    private val usedAar = generateAarWithContent("com.analyzedependenciesTest.usedaar",
            Resources.toByteArray(
                    Resources.getResource(
                            AnalyzeDependenciesTest::class.java,
                            "AnalyzeDependenciesTest/used-jar.jar")
            )
    )

    private val app = MinimalSubProject.app("com.example.app")
            .appendToBuild(
                    """
                dependencies {
                implementation 'com.analyzedependenciesTest:emptyaar:1'
                implementation 'com.analyzedependenciesTest:usedaar:1'
                 }
            """.trimIndent()
            )
            .withFile("src/main/java/com/example/app/MyClass.java",
                    """
                package com.example.app;
                
                import com.android.build.gradle.integration.application.AnalyzeDependenciesTest.UsedClass;
                
                public class MyClass {
                    void test() {
                        UsedClass usedClass = new UsedClass();
                        usedClass.getTrue();
                    }
                }
            """.trimIndent())


    private val mavenRepo = MavenRepoGenerator(
            listOf(
                    MavenRepoGenerator.Library(
                            "com.analyzedependenciesTest:emptyaar:1", "aar", emptyAar),
                    MavenRepoGenerator.Library(
                            "com.analyzedependenciesTest:usedaar:1", "aar", usedAar)
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
        val buildType = "debug"
        project.execute(":app:assemble${buildType.toCamelCase()}")
        project.execute(":app:analyze${buildType.toCamelCase()}Dependencies")

        val dependencyAnalysisReport = project.getSubproject(":app").getIntermediateFile(
                "analyze_dependencies_report",
                buildType,
                "analyzeDependencies",
                "dependenciesReport.json"
        )
        assertThat(dependencyAnalysisReport.exists())

        val dependencyReportJson = dependencyAnalysisReport.readText()
        val parsedJson =
                Gson().fromJson(dependencyReportJson, DependenciesUsageReport::class.java)

        assertThat(parsedJson.add.size).isEqualTo(0)
        assertThat(parsedJson.remove.size).isEqualTo(1)
        assertThat(parsedJson.remove).contains("com.analyzedependenciesTest:emptyaar:1")
    }
}