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
package com.android.build.gradle.integration.connected.testing

import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.builder.model.v2.models.AndroidProject
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.generateAarWithContent
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.fail

class CustomTestedApksTest {

    private val mavenRepo = MavenRepoGenerator(
        listOf(
            MavenRepoGenerator.Library(
                "com.example:library:1",
                "aar",
                generateAarWithContent(
                    packageName = "com.example.library",
                    mainJar = TestInputsGenerator.jarWithEmptyClasses(listOf("com/example/library/MyClass")),
                    resources = mapOf("layout/lib_layout.xml" to """<LinearLayout/>""".toByteArray())
                )
            )
        )
    )

    private fun aarUser(packageName: String) = TestSourceFile("src/main/java/${packageName.replace('.','/')}/Util.java", //language=java
        """
                package $packageName;

                // To check the app is compiled against the class
                import com.example.library.MyClass;

                public class Util {
                    public static MyClass useAar() {
                        return new MyClass();
                    }
                }
        """.trimIndent())

    private val app = MinimalSubProject.app("com.example.app").apply {
        appendToBuild(//language=groovy
            """
            android {
                buildTypes {
                    benchmark {
                        debuggable = false
                        signingConfig = debug.signingConfig
                    }
                }
                dependenciesInfo.includeInApk = false //https://issuetracker.google.com/162074215
            }
            """.trimIndent()
        )
        addFile(
            "src/benchmark/AndroidManifest.xml", //language=xml
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application>
                         <!--suppress AndroidElementNotAllowed -->
                        <profileable android:shell="true"/>
                    </application>
                </manifest>
                """.trimIndent()
        )
        addFile(aarUser("com.example.app"))
    }

    private val test = MinimalSubProject.test("com.example.app.benchmark").apply {
        appendToBuild( //language=groovy
            """
                android {
                    defaultConfig.testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'
                    buildTypes {
                        benchmark {
                            debuggable = true
                            signingConfig = debug.signingConfig
                        }
                    }
                    targetProjectPath = ":app"
                    experimentalProperties["android.experimental.self-instrumenting"] = true
                }

                androidComponents {
                   beforeVariants(selector().all()) {
                        enabled = buildType == 'benchmark'
                    }
                }

                dependencies {
                    implementation 'androidx.test.ext:junit:1.1.2'
                    implementation 'androidx.test:runner:1.3.0'
                    implementation 'androidx.test:rules:1.3.0'
                }

            """.trimIndent()
        )
        addFile(
            "src/main/java/com/example/app/benchmark/MyTest.java",
            //language=java
            """
                package com.example.app.benchmark;

                import static org.junit.Assert.assertTrue;
                import org.junit.runner.RunWith;
                import org.junit.Test;
                import androidx.test.ext.junit.runners.AndroidJUnit4;
                import androidx.test.platform.app.InstrumentationRegistry;
                import android.content.pm.ApplicationInfo;


                @RunWith(AndroidJUnit4.class)
                public class MyTest {
                    @Test
                    public void checkProfileable() throws Exception {
                        ApplicationInfo info = InstrumentationRegistry.getInstrumentation()
                                .getContext()
                                .getPackageManager()
                                .getApplicationInfo("com.example.app", 0);
                        assertTrue("com.example.app should be profileable by shell", info.isProfileableByShell());
                    }
                }
                """.trimIndent()
        )
        addFile(aarUser("com.example.app.benchmark"))
    }

    private val androidTestUtilExample =
        MultiModuleTestProject.builder()
            .subproject("app", app)
            .dependency(app, "com.example:library:1")
            .subproject("test", test)
            .dependency(test, "com.example:library:1")
            .build()

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(androidTestUtilExample)
            .withAdditionalMavenRepo(mavenRepo)
            .create()

    @Before
    fun setUp() {
        project.addUseAndroidXProperty()
        // fail fast if no response
        project.addAdbTimeout()
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll")
    }

    @Test
    fun connectedCheckInstalls() {
        val appModel = project.executeAndReturnModel(":test:connectedCheck")

        val testVariant = appModel.onlyModelMap[":test"]?.variants?.first()
            ?: fail("cannot get test Variant")
        val testedTargetVariants = testVariant.testedTargetVariants
        Truth.assertThat(testedTargetVariants.size).isEqualTo(1)
        Truth.assertThat(testedTargetVariants.first().targetProjectPath).isEqualTo(":app")
        Truth.assertThat(testedTargetVariants.first().targetVariant).isEqualTo("benchmark")

        // check the benchmark manifest file, it should self instrument itself.
        val packagedManifestFolder = FileUtils.join(project.getSubproject("test").buildDir,
                AndroidProject.FD_INTERMEDIATES,
                InternalArtifactType.PACKAGED_MANIFESTS.getFolderName(),
                "benchmark")
        Truth.assertThat(packagedManifestFolder.exists()).isTrue()
        val manifests = BuiltArtifactsLoaderImpl.loadFromDirectory(packagedManifestFolder)
                ?: throw RuntimeException("No manifest file generated !")
        Truth.assertThat(manifests.elements.size).isEqualTo(1)
        val manifestFile = File(manifests.elements.single().outputFile)
        Truth.assertThat(manifestFile.exists()).isTrue()
        Truth.assertThat(manifestFile.readText()).contains("" +
                "android:targetPackage=\"com.example.app.benchmark\" />")

        val testOnlyApk =
            project.getSubproject("test").getApk(GradleTestProject.ApkType.of("benchmark", true));
        TruthHelper.assertThat(testOnlyApk).containsClass("Lcom/example/library/MyClass;")
        TruthHelper.assertThat(testOnlyApk).containsResource("layout/lib_layout.xml")
    }

    companion object {

        @get:ClassRule
        @get:JvmStatic
        val emulator = getEmulator()
    }

}
