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

package com.android.build.gradle.integration.common.fixture.app

import com.android.build.gradle.integration.common.fixture.ANDROIDX_CONSTRAINT_LAYOUT_VERSION
import com.android.build.gradle.integration.common.fixture.ANDROIDX_VERSION
import com.android.build.gradle.integration.common.fixture.EmptyGradleProject
import com.android.build.gradle.integration.common.fixture.GradleProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_CONSTRAINT_LAYOUT_VERSION
import com.android.build.gradle.options.BooleanOption
import java.io.File

/**
 * Builder for a [GradleTestProject] with an empty activity. It resembles the New Project wizard in
 * Android Studio.
 *
 * By default, the root project includes an app sub-project. It may also include additional
 * sub-projects if they are provided to this builder.
 */
class EmptyActivityProjectBuilder {

    /*
     * The following settings resemble the New Project wizard in Android Studio
     */
    var projectName: String = "My Application"
    var packageName: String = "com.example.myapplication"
    var minApiLevel: String = "15"
    var useKotlin: Boolean = false

    /*
     * The following are additional settings to further customize the project
     */
    var useAndroidX: Boolean = true
    var withUnitTest: Boolean = true
    var useGradleBuildCache: Boolean = false
    var gradleBuildCacheDir: File? = null

    /*
     * This allows to add more subprojects
     */
    var additionalSubProjects: List<GradleProject> = listOf()

    init {
        if (useGradleBuildCache) {
            checkNotNull(gradleBuildCacheDir) {
                "gradleBuildCacheDir must be specified when useGradleBuildCache=true"
            }
        }
    }

    fun build(): GradleTestProject {
        val subProjectsBuilder = MultiModuleTestProject.builder()
        val appSubProject =
            createAppSubProject(packageName, minApiLevel, useKotlin, useAndroidX, withUnitTest)
        for (subProject in listOf(appSubProject) + additionalSubProjects) {
            subProjectsBuilder.subproject(subProject.name!!, subProject)
        }

        val rootProjectBuilder = GradleTestProject.builder()
            .withName(projectName)
            .withKotlinGradlePlugin(useKotlin)
            .fromTestApp(subProjectsBuilder.build())

        if (useAndroidX) {
            rootProjectBuilder
                .addGradleProperties(BooleanOption.USE_ANDROID_X.propertyName + "=true")
                .addGradleProperties(BooleanOption.ENABLE_JETIFIER.propertyName + "=true")
        }

        if (useGradleBuildCache) {
            rootProjectBuilder.withGradleBuildCacheDirectory(gradleBuildCacheDir!!)
        }

        return rootProjectBuilder.create()
    }

    private fun createAppSubProject(
        packageName: String,
        minApiLevel: String,
        useKotlin: Boolean,
        useAndroidX: Boolean,
        withUnitTest: Boolean
    ): GradleProject {
        val app = EmptyGradleProject("app")
        val packagePath = packageName.replace('.', '/')

        // 1. Create build.gradle file
        app.replaceFile(
            "build.gradle",
            with(BuildFileBuilder()) {
                plugin = "com.android.application"
                this.useKotlin = useKotlin
                compileSdkVersion = GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                minSdkVersion = minApiLevel
                if (useAndroidX) {
                    addDependency(
                        dependency = "'androidx.appcompat:appcompat:$ANDROIDX_VERSION'"
                    )
                    addDependency(
                        dependency = "'androidx.constraintlayout:constraintlayout:" +
                                "$ANDROIDX_CONSTRAINT_LAYOUT_VERSION'"
                    )
                } else {
                    addDependency(
                        dependency = "'com.android.support:appcompat-v7:$SUPPORT_LIB_VERSION'"
                    )
                    addDependency(
                        dependency = "'com.android.support.constraint:constraint-layout:" +
                                "$SUPPORT_LIB_CONSTRAINT_LAYOUT_VERSION'"
                    )
                }
                if (withUnitTest) {
                    addDependency(
                        configuration = "testImplementation",
                        dependency = "'junit:junit:4.12'"
                    )
                }
                build()
            }
        )

        // 2. Create AndroidManifest.xml file
        app.replaceFile(
            "src/main/AndroidManifest.xml",
            with(ManifestFileBuilder(packageName)) {
                addApplicationTag("MainActivity")
                build()
            })

        // 3. Create source files
        val appCompatActivityClass = if (useAndroidX) {
            "androidx.appcompat.app.AppCompatActivity"
        } else {
            "android.support.v7.app.AppCompatActivity"
        }
        if (useKotlin) {
            app.replaceFile(
                "src/main/java/$packagePath/MainActivity.kt",
                """
                package $packageName

                import $appCompatActivityClass
                import android.os.Bundle

                class MainActivity : AppCompatActivity() {

                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(R.layout.activity_main)
                    }
                }
                """.trimIndent()
            )
            if (withUnitTest) {
                app.replaceFile(
                    "src/test/java/$packagePath/ExampleUnitTest.kt",
                    """
                    package $packageName

                    import org.junit.Test

                    import org.junit.Assert.*

                    /**
                     * Example local unit test, which will execute on the development machine (host).
                     *
                     * See [testing documentation](http://d.android.com/tools/testing).
                     */
                    class ExampleUnitTest {
                        @Test
                        fun addition_isCorrect() {
                            assertEquals(4, 2 + 2)
                        }
                    }

                    """.trimIndent()
                )
            }
        } else {
            app.replaceFile(
                "src/main/java/$packagePath/MainActivity.java",
                """
                package $packageName;

                import $appCompatActivityClass;
                import android.os.Bundle;

                public class MainActivity extends AppCompatActivity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.activity_main);
                    }
                }
                """.trimIndent()
            )
            if (withUnitTest) {
                app.replaceFile(
                    "src/test/java/$packagePath/ExampleUnitTest.java",
                    """
                    package $packageName;

                    import org.junit.Test;

                    import static org.junit.Assert.*;

                    /**
                     * Example local unit test, which will execute on the development machine (host).
                     *
                     * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
                     */
                    public class ExampleUnitTest {
                        @Test
                        public void addition_isCorrect() {
                            assertEquals(4, 2 + 2);
                        }
                    }
                    """.trimIndent()
                )
            }
        }

        // 4. Create layout file
        app.replaceFile(
            "src/main/res/layout/activity_main.xml",
            with(LayoutFileBuilder()) {
                this.useAndroidX = useAndroidX
                addTextView("helloTextId", "Hello World!")
                build()
            }
        )

        return app
    }
}