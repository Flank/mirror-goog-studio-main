package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.application.testData.EnumClass
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.tasks.DependenciesUsageReport
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.generateAarWithContent
import com.android.utils.usLocaleCapitalize
import com.google.common.io.Resources
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.Charset

/**
 * Tests for [AnalyzeDependenciesTask]
 */
class AnalyzeDependenciesTest {

    private val emptyAar = generateAarWithContent("com.analyzedependenciesTest.emptyAar")

    private val usedClassAar = generateAarWithContent("com.analyzedependenciesTest.usedClassAar",
            Resources.toByteArray(
                    Resources.getResource(
                            AnalyzeDependenciesTest::class.java,
                            "AnalyzeDependenciesTest/used-jar.jar")
            )
    )

    private val usedResourceAar = generateAarWithContent("com.analyzedependenciesTest.usedResAar",
            resources = mapOf("values/strings.xml" to
                    // language=XML
                    """<?xml version="1.0" encoding="utf-8"?>
                    <resources>
                    <string name="used_text">This string is used.</string>
                    </resources>""".trimIndent().toByteArray(Charset.defaultCharset())
            )
    )

    private val unUsedResourceAar = generateAarWithContent("com.analyzedependenciesTest.unUsedResAar",
            resources = mapOf("values/strings.xml" to
                    // language=XML
                    """<?xml version="1.0" encoding="utf-8"?>
                    <resources>
                    <string name="unused_text">This string is not used.</string>
                    </resources>""".trimIndent().toByteArray(Charset.defaultCharset())
            )
    )

    private val app = MinimalSubProject.app("com.example.app")
            .appendToBuild("""
                dependencies {
                implementation project(path: ':usedClassLocalLib')
                implementation project(path: ':unUsedLocalLib')
                implementation 'com.analyzedependenciesTest:emptyAar:1'
                implementation 'com.analyzedependenciesTest:usedClassAar:1'
                implementation 'com.analyzedependenciesTest:unUsedResAar:1'
                implementation 'com.analyzedependenciesTest:usedResAar:1'
                implementation 'com.analyzedependenciesTest:usedJar:1'
                implementation 'com.analyzedependenciesTest:unusedJar:1'
                 }
            """.trimIndent()
            ).withFile(
                    "src/main/AndroidManifest.xml",
                    //language=XML
                    """
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                                  xmlns:tools="http://schemas.android.com/tools"
                                  package="com.example.app">
                            <application>
                                <receiver android:name=".MyReceiver"
                                          android:label="app">
                                    <meta-data
                                            android:name="com.example.sdk.ApplicationId"
                                            android:value="1"/>
                                </receiver>
                            </application>
                        </manifest>
                    """.trimIndent()
            )
            .withFile("src/main/java/com/example/app/MyClass.java",
                    // language=JAVA
                """package com.example.app;
                
                import com.android.build.gradle.integration.application.AnalyzeDependenciesTest.UsedClass;
                import com.android.build.gradle.integration.application.testData.EnumClass;
                import com.example.usedclasslocallib.UsedClassLocalLib;
                
                public class MyClass {
                    public static final EnumClass enumClass = EnumClass.ONE;
                    void testUsedAarClass() {
                        UsedClass usedClass = new UsedClass();
                        usedClass.getTrue();
                    }
                    void testUsedClassLocalLibClass() {
                        UsedClassLocalLib usedClassLocalLib = new UsedClassLocalLib();
                        usedClassLocalLib.getFoo();
                    }
                }
            """.trimIndent())
        // used_text is declared in com.analyzedependenciesTest.usedResAar .
        .withFile("src/main/res/layout/main_activity.xml",
            // language=XML
            """<?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <TextView
                        android:id="@+id/text_box"
                        android:text="@string/used_text"
                        android:layout_x="10px"
                        android:layout_y="110px"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>""".trimIndent()
        )

    private val usedClassLocalLib = MinimalSubProject.lib("com.example.usedClasslocallib")
            .withFile("src/main/java/com/example/usedclasslocallib/UsedClassLocalLib.java",
                    // language=JAVA
                    """
                package com.example.usedclasslocallib;

                public class UsedClassLocalLib {
                    public String getFoo() {
                        return "Foo";
                    }
                }
            """.trimIndent())

    private val unUsedLocalLib = MinimalSubProject.lib("com.example.unusedlocallib")
            .withFile("src/main/java/com/example/usedlocallib/UnUsedLocalLib.java",
                    // language=JAVA
                    """
                package com.example.unusedlocallib;

                public class UnUsedLocalLib {
                    public String getBar() {
                        return "Bar";
                    }
                }""".trimIndent())
        .withFile("src/main/res/layout/layout_random_name.xml",
                // language=XML
                """<?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <TextView
                        android:id="@+id/text_box"
                        android:text="test"
                        android:layout_x="10px"
                        android:layout_y="110px"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>""".trimIndent())

    private val usedJar = TestInputsGenerator.jarWithClasses(listOf(EnumClass::class.java))
    private val emptyJar = TestInputsGenerator.jarWithClasses(emptyList())

    private val mavenRepo = MavenRepoGenerator(
            listOf(
                    MavenRepoGenerator.Library(
                            "com.analyzedependenciesTest:emptyAar:1", "aar", emptyAar),
                    MavenRepoGenerator.Library(
                            "com.analyzedependenciesTest:usedClassAar:1", "aar", usedClassAar),
                    MavenRepoGenerator.Library(
                            "com.analyzedependenciesTest:usedResAar:1", "aar", usedResourceAar),
                    MavenRepoGenerator.Library(
                            "com.analyzedependenciesTest:unUsedResAar:1", "aar", unUsedResourceAar),
                    MavenRepoGenerator.Library("com.analyzedependenciesTest:usedJar:1", usedJar),
                    MavenRepoGenerator.Library("com.analyzedependenciesTest:unusedJar:1", emptyJar)
            )
    )

    private val testApp =
            MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":usedClassLocalLib", usedClassLocalLib)
                    .subproject(":unUsedLocalLib", unUsedLocalLib)
                    .dependency(app, usedClassLocalLib)
                    .dependency(app, unUsedLocalLib)
                    .build()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    val project = GradleTestProject.builder()
            .withAdditionalMavenRepo(mavenRepo)
            .fromTestApp(testApp)
            // http://b/149978740
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .create()

    @Test
    fun `Verify correct dependencies report is produced, only considering class and resource references`() {
        val buildType = "debug"
        project.execute(
            ":app:assemble${buildType.usLocaleCapitalize()}",
            ":app:analyze${buildType.usLocaleCapitalize()}Dependencies"
        )

        val dependencyAnalysisReport = project.getSubproject(":app").getIntermediateFile(
            InternalArtifactType.ANALYZE_DEPENDENCIES_REPORT.getFolderName(),
            buildType,
            "analyzeDependencies",
            "dependenciesReport.json"
        )
        assertThat(dependencyAnalysisReport.exists())

        val dependencyReportJson = dependencyAnalysisReport.readText()
        val parsedJson =
            Gson().fromJson(dependencyReportJson, DependenciesUsageReport::class.java)

        assertThat(parsedJson.remove).containsExactly(
            "com.analyzedependenciesTest:emptyAar:1",
            "com.analyzedependenciesTest:unUsedResAar:1",
            "com.analyzedependenciesTest:unusedJar:1"
        )
        assertThat(parsedJson.add.size).isEqualTo(0)
    }
}