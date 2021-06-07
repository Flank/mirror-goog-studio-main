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
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.DEFAULT_MIN_SDK_VERSION
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
 * The root project includes an app subproject and may also include additional subprojects if they
 * are added to the root project.
 */
class EmptyActivityProjectBuilder {

    /*
     * The following settings resemble the New Project wizard in Android Studio
     */
    var projectName: String = "My Application"
    var packageName: String = COM_EXAMPLE_MYAPPLICATION
    var minSdkVersion: Int = DEFAULT_MIN_SDK_VERSION
    var useKotlin: Boolean = false

    /*
     * The following are additional settings to further customize the project
     */
    var withUnitTest: Boolean = false
    var useAndroidX: Boolean = true
    var useGradleBuildCache: Boolean = false
    var gradleBuildCacheDir: File? = null
    var withConfigurationCaching: BaseGradleExecutor.ConfigurationCaching =
            BaseGradleExecutor.ConfigurationCaching.ON

    /** The app subproject. */
    private lateinit var app: GradleProject

    /** The library subprojects. */
    private val librarySubProjects: MutableList<GradleProject> = mutableListOf()

    /** The library subprojects that app depends on. */
    private val appDependencies: MutableList<GradleProject> = mutableListOf()

    /**
     * Whether Kotlin is used in any of the library subprojects.
     *
     * It is a `var` because its value will be updated when library subprojects are added.
     */
    private var kotlinUsedInLibrarySubprojects: Boolean = false

    init {
        if (useGradleBuildCache) {
            checkNotNull(gradleBuildCacheDir) {
                "gradleBuildCacheDir must be specified when useGradleBuildCache=true"
            }
        }
    }

    fun build(): GradleTestProject {
        val subProjectsBuilder = MultiModuleTestProject.builder()
        app = createAppSubProject(APP, packageName, minSdkVersion, useKotlin)
        subProjectsBuilder.subproject(app.path!!, app)
        for (subProject in librarySubProjects) {
            subProjectsBuilder.subproject(subProject.path!!, subProject)
        }

        val rootProjectBuilder = GradleTestProject.builder()
            .withName(projectName)
            .fromTestApp(subProjectsBuilder.build())
            .withConfigurationCaching(withConfigurationCaching)

        rootProjectBuilder.withKotlinGradlePlugin(useKotlin || kotlinUsedInLibrarySubprojects)

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
        @Suppress("SameParameterValue") subprojectName: String,
        packageName: String,
        minSdkVersion: Int,
        useKotlin: Boolean
    ): GradleProject {
        val app = EmptyGradleProject(subprojectName)
        val packagePath = packageName.replace('.', '/')

        // 1. Create build.gradle file
        app.addFile(
            "build.gradle",
            with(BuildFileBuilder()) {
                plugin = "com.android.application"
                this.useKotlin = useKotlin
                compileSdkVersion = GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                this.minSdkVersion = minSdkVersion.toString()
                appDependencies.forEach {
                    addDependency(dependency = "project(\"${it.path}\")")
                }
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
        app.addFile(
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
            app.addFile(
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
        } else {
            app.addFile(
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
        }
        if (withUnitTest) {
            addUnitTest(app, packageName, useKotlin)
        }

        // 4. Create layout file
        app.addFile(
            "src/main/res/layout/activity_main.xml",
            with(LayoutFileBuilder()) {
                this.useAndroidX = (this@EmptyActivityProjectBuilder).useAndroidX
                addTextView("helloTextId", "Hello World!")
                build()
            }
        )

        return app
    }

    private fun addUnitTest(subProject: GradleProject, packageName: String, useKotlin: Boolean) {
        val packagePath = packageName.replace('.', '/')
        if (useKotlin) {
            subProject.addFile(
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
        } else {
            subProject.addFile(
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

    /**
     * Adds an Android library subproject to this project.
     *
     * It resembles the New Module wizard in Android Studio.
     */
    fun addAndroidLibrary(
        subprojectName: String = LIB,
        packageName: String = COM_EXAMPLE_LIB,
        minSdkVersion: Int = DEFAULT_MIN_SDK_VERSION,
        useKotlin: Boolean = false,
        addImplementationDependencyFromApp: Boolean = false // default to false to match AS behavior
    ): EmptyActivityProjectBuilder {
        val lib = EmptyGradleProject(subprojectName)

        // 1. Create build.gradle file
        lib.addFile(
            "build.gradle",
            with(BuildFileBuilder()) {
                plugin = "com.android.library"
                this.useKotlin = useKotlin
                compileSdkVersion = GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                this.minSdkVersion = minSdkVersion.toString()
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
        lib.addFile(
            "src/main/AndroidManifest.xml",
            with(ManifestFileBuilder(packageName)) {
                build()
            })

        // 3. Create source files
        if (withUnitTest) {
            addUnitTest(lib, packageName, useKotlin)
        }

        librarySubProjects.add(lib)
        if (addImplementationDependencyFromApp) {
            appDependencies.add(lib)
        }
        if (useKotlin) {
            kotlinUsedInLibrarySubprojects = true
        }
        return this
    }

    /**
     * Adds a Java library subproject to this project.
     *
     * It resembles the New Module wizard in Android Studio.
     */
    fun addJavaLibrary(
        subprojectName: String = JAVALIB,
        useKotlin: Boolean = false,
        addImplementationDependencyFromApp: Boolean = false // default to false to match AS behavior
    ): EmptyActivityProjectBuilder {
        val lib = EmptyGradleProject(subprojectName)

        lib.addFile(
            "build.gradle",
            with(BuildFileBuilder()) {
                plugin = "java-library"
                this.useKotlin = useKotlin
                build()
            }
        )

        librarySubProjects.add(lib)
        if (addImplementationDependencyFromApp) {
            appDependencies.add(lib)
        }
        if (useKotlin) {
            kotlinUsedInLibrarySubprojects = true
        }
        return this
    }

    companion object {
        const val APP = "app"
        const val LIB = "lib"
        const val JAVALIB = "javalib"
        const val COM_EXAMPLE_MYAPPLICATION = "com.example.myapplication"
        const val COM_EXAMPLE_LIB = "com.example.lib"
    }
}
