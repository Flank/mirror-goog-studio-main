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

package com.android.build.api.apiTest

import com.android.SdkConstants
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.AbstractBuildGivenBuildCheckTest
import com.android.testutils.TestResources
import com.android.testutils.TestUtils
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import java.io.File
import java.io.FileReader
import java.nio.file.Files
import java.util.Properties
import java.util.logging.Logger

/**
 * Base test class for Variant API related tasks. These tasks setup a Gradle project and execute
 * some tasks on those and finally verifies the build behavior or output.
 *
 * @param testType the test type.
 * @param scriptingLanguage the language used to express the build logic.
 */
open class VariantApiBaseTest(
    private val testType: TestType,
    protected val scriptingLanguage: ScriptingLanguage = ScriptingLanguage.Kotlin
) :
    AbstractBuildGivenBuildCheckTest<VariantApiBaseTest.GivenBuilder, BuildResult>() {

    companion object {

        /**
         * AGP version which can be overridden when invoking the test execution.
         */
        val agpVersion = System.getenv("API_TESTS_VERSION")
                ?: com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION

        /**
         * If running within a build system, make sure to use the expected version when generating
         * build.gradle.kts files.
         */
        val kotlinVersion: String by lazy {
            System.getenv("KOTLIN_PLUGIN")?.split(':')?.last()
            // fall back, use the version I am running against.
                    ?: KotlinVersion.CURRENT.toString()
        }

        val generalRepos = listOf(
                "google()",
                "jcenter()")

        /**
         * List of custom repositories where all projects dependencies can be satisfied.
         */
        val mavenRepos: List<String> by lazy {
            System.getenv("CUSTOM_REPO").split(File.pathSeparatorChar)
        }

    }

    /**
     * Type of test.
     */
    enum class TestType {

        /**
         * In [Script] tests, all build logic is expressed in build file scripts.
         */
        Script {

            override fun getDirName(test: VariantApiBaseTest): String {
                return test.scriptingLanguage.name
            }
        },

        /**
         * In [BuildSrc] tests, build customization is done through a buildSrc plugin.
         */
        BuildSrc,

        /**
         * In [Plugin] tests, build customization is done through a binary plugin dependency.
         */
        Plugin;

        open fun getDirName(test: VariantApiBaseTest): String = name
    }

    /**
     * Supported scripting languages for expressing build logic, right now, Kotlin and Groovy.
     *
     * @param buildFileName name of the module build file for the language.
     * @param settingsFileName name of the settings file name for the language.
     */
    enum class ScriptingLanguage(val buildFileName: String, val settingsFileName: String) {

        /**
         * Kotlin scripting languages,
         */
        Kotlin("build.gradle.kts", "settings.gradle.kts") {

            override fun configureBuildFile(
                repositories: List<String>,
                additionalClasspath: List<String>
            ) =
                    """
buildscript {
${addGlobalRepositories(repositories).prependIndent("    ")}
    dependencies {
        classpath("com.android.tools.build:gradle:${agpVersion}")
        classpath(kotlin("gradle-plugin", version = "$kotlinVersion"))
        ${addClasspath(additionalClasspath)}
    }
}
allprojects {
    ${addGlobalRepositories(generalRepos).prependIndent("    ")}
}
"""

            // we should not have to add the gobalRepos below but because of
            // https://github.com/gradle/gradle/issues/1055
            override fun configureBuildSrcBuildFile(globalRepos: List<String>, localRepos: List<String>) =
                    """
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "$kotlinVersion"
}
${addRepositories(localRepos)}
${addGlobalRepositories(globalRepos)}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.apiVersion = "1.3"
}

dependencies {
    implementation("com.android.tools.build:gradle-api:${agpVersion}")
    implementation(kotlin("stdlib"))
    gradleApi()
}
"""

            private fun addRepositories(repositories: List<String>) =
                    """
repositories {
${
                        repositories.joinToString(
                                separator = "\")\n    maven(\"",
                                prefix = "    maven(\"",
                                postfix = "\")"
                        )
                    }
}
"""

            override fun configureInitScript(repositories: List<String>): String =
                    """
allprojects {
    buildscript {
    ${addRepositories(repositories).prependIndent("        ")}
    }
    ${addRepositories(repositories).prependIndent("    ")}
}
"""

            override fun makeScriptFileName(root: String): String {
                return "$root.kts"
            }
        },
        Groovy("build.gradle", "settings.gradle") {

            override fun configureBuildFile(
                repositories: List<String>,
                additionalClasspath: List<String>
            ) =
                    """
buildscript {
${addGlobalRepositories(repositories).prependIndent("    ")}
    dependencies {
        classpath("com.android.tools.build:gradle:${agpVersion}")
        ${addClasspath(additionalClasspath)}
    }
}
allprojects {
    ${addGlobalRepositories(generalRepos).prependIndent("    ")}
}
"""

            override fun configureBuildSrcBuildFile(globalRepos: List<String>, localRepos: List<String>) =
                    """
"""

            override fun configureInitScript(repositories: List<String>): String =
                    """
allprojects {
    buildscript {
${addRepositories(repositories).prependIndent("        ")}
    }
${addRepositories(repositories).prependIndent("    ")}
}
"""

            override fun makeScriptFileName(root: String): String {
                return root
            }

            private fun addRepositories(repositories: List<String>) =
                    """
repositories {
${
                        repositories.joinToString(
                                separator = "\'}\n    maven { url \'",
                                prefix = "    maven { url \'",
                                postfix = "\'}"
                        )
                    }
}
"""
        };

        protected fun addGlobalRepositories(repositories: List<String>) =
                repositories.joinToString(
                        separator = "\n    ",
                        prefix = "repositories {\n    ",
                        postfix = "\n}"
                )

        protected fun addClasspath(classpath: List<String>) =
            if (classpath.isNotEmpty()) {
                classpath.joinToString(")\nclasspath(\"", "classpath(\"", "\")")
            } else ""

        /**
         * Configure a module build file for the [scriptingLanguage].
         *
         * By default the AGP plugin will be automatically added, depending on the scripting
         * language, other plugins like the kotlin gradle plugin when using kotlin for instance.
         *
         * @param repositories the list of repositories where project dependencies will be obtained
         * from
         */
        abstract fun configureBuildFile(
            repositories: List<String>,
            additionalClasspath: List<String> = listOf()
        ): String

        /**
         * Configure the buildSrc/ build file for the [scriptingLanguage]
         *
         * @param globalRepos the list of global repositories where project dependencies will be
         * obtained from.
         * @param localRepos the list of repositories where project dependencies will be obtained
         * from
         */
        abstract fun configureBuildSrcBuildFile(globalRepos: List<String>, localRepos: List<String>): String

        /**
         * Configure the init script file for the [scriptingLanguage]
         *
         * @param repositories the list of local repositories where project dependencies will be
         * obtained from
         */
        abstract fun configureInitScript(repositories: List<String>): String

        abstract fun makeScriptFileName(root: String): String
    }

    @Rule
    @JvmField
    val testName = TestName()

    @Rule
    @JvmField
    val testProjectDir = if (System.getenv("API_TESTS_OUTDIR") != null) {
        ApiTestFolder(File(System.getenv("API_TESTS_OUTDIR")), testType.getDirName(this))
    } else {
        TemporaryFolder()
    }

    @Rule
    @JvmField
    val privateTestProjectDir = TemporaryFolder()

    @Rule
    @JvmField
    val testBuildDir = TemporaryFolder()

    val testingElements = TestingElements(scriptingLanguage)

    open fun sdkLocation(): String = TestUtils.getSdk().toString()

    open class ModuleGivenBuilder(val scriptingLanguage: ScriptingLanguage) {

        /**
         * Manifest file for the module, optional.
         */
        var manifest: String? = null

        /**
         * Build file for the module, must be provided by the time
         * [AbstractBuildGivenBuildCheckTest.when] is called.
         */
        var buildFile: String? = null

        private val sourceFiles = mutableListOf<Pair<String, String>>()

        private val customFolders = mutableListOf<Pair<String, File.()-> Unit>>()

        /**
         * Add a source file to the current module
         *
         * @param path the relative path from the module root folder to store the source file in.
         * @param content the source file content.
         */
        fun addSource(path: String, content: String) {
            sourceFiles.add(Pair(path, content))
        }

        /**
         * Add a directory to the current module.
         *
         * @param path the relative path from the module root folder to create the directory at.
         * @param action: the lambda to execute once the directory is created to configure its
         * content.
         */
        fun addDirectory(path: String, action: File.() -> Unit) {
            customFolders.add(path to action)
        }

        internal open fun writeModule(folder: File) {
            Truth.assertThat(buildFile != null)

            addBuildFile(folder)

            if (manifest != null) {
                File(folder, "src/main").apply {
                    mkdirs()
                    File(
                            this,
                            SdkConstants.ANDROID_MANIFEST_XML
                    ).writeText(manifest!!.trimIndent())
                }
            }

            sourceFiles.forEach {
                val isSource = it.first.endsWith(".java") || it.first.endsWith(".kt")
                File(folder, it.first).apply {
                    parentFile.mkdirs()
                    if (isSource) {
                        writeText("${license()}\n${it.second.trimIndent()}")
                    } else {
                        writeText(it.second.replaceIndent())
                    }
                }
            }

            customFolders.forEach { (folderPath, folderAction) ->
                val newFolder = File(folder, folderPath)
                newFolder.mkdirs()
                folderAction.invoke(newFolder)
            }
        }

        internal open fun addBuildFile(folder: File) {
            File(folder, scriptingLanguage.buildFileName).apply {
                writeText(scriptingLanguage.configureBuildFile(generalRepos) + (buildFile ?: ""))
            }
        }

        internal fun license() =
                """
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
            """.trimIndent()
    }

    open class GivenBuilder(scriptingLanguage: ScriptingLanguage,
            val testName: TestName? = null)
        : ModuleGivenBuilder(scriptingLanguage) {

        private val modules = mutableListOf<Pair<String, GivenBuilder>>()


        /**
         * the list of tasks' names to invoke on the project, by default, we only invoke
         * the assembleDebug task.
         */
        internal val tasksToInvoke = mutableListOf<String>()

        internal var failureExpected = false

        fun expectFailure() {
            failureExpected = true
        }

        private fun modulesPath(): List<String> = modules.filter { it.first != "buildSrc" }.map { it.first }

        /**
         * Add a new module to the current module and open a new block to configure it.
         *
         * @param path the Gradle relative path for the module, must start with ':'
         * @param action the configuration block for the module.
         */
        fun addModule(path: String, action: GivenBuilder.() -> Unit) {
            if (path == "buildSrc") throw RuntimeException("Use addBuildSrc() for buildSrc module")
            object: GivenBuilder(scriptingLanguage) {
                override fun addBuildFile(folder: File) {
                    if (buildFile != null) {
                        File(folder, scriptingLanguage.buildFileName).apply {
                            writeText(buildFile!!)
                        }
                    }
                }
            }.also {
                modules.add(Pair(path, it))
                action.invoke(it)
            }
        }

        /**
         * Add a buildSrc module to the current module.
         *
         * @param action the configuration block for the buildSrc module.
         */
        fun addBuildSrc(action: GivenBuilder.() -> Unit) {
            object: GivenBuilder(scriptingLanguage) {
                override fun addBuildFile(folder: File) {
                    File(folder, scriptingLanguage.buildFileName).apply {
                        writeText(scriptingLanguage.configureBuildSrcBuildFile(generalRepos, mavenRepos).trimIndent() + "\n" + (buildFile ?: ""))
                    }
                }
            }.also {
                modules.add(Pair("buildSrc", it))
                action.invoke(it)
            }
        }

        fun writeModule(folder: File, sdkLocation: String) {
            val settingsFile = File(folder, scriptingLanguage.settingsFileName)

            val includeList = modulesPath().joinToString(
                separator = "\")\ninclude(\"",
                prefix = "include(\"",
                postfix = "\")\n"
            )

            settingsFile.writeText(
"""
$includeList
rootProject.name = "${testName?.methodName ?: javaClass.simpleName}"
""")

            super.writeModule(folder)

            modules.forEach { (path, givenBuilder) ->
                File(folder, path.replace(':', File.separatorChar)).apply {
                    mkdirs()
                    givenBuilder.writeModule(this)
                }
            }
        }

        private val additionalClasspath = mutableListOf<String>()

        fun addClasspath(classpath: String) {
            additionalClasspath.add(classpath)
        }

        override fun addBuildFile(folder: File) {
            File(folder, scriptingLanguage.buildFileName).apply {
                writeText(scriptingLanguage.configureBuildFile(generalRepos, additionalClasspath) + (buildFile ?: ""))
            }
        }
    }

    private var docs = DocumentationBuilder()

    private val parameters: MutableMap<BooleanOption, Any> = mutableMapOf()

    open class DocumentationBuilder {
        var index: String? = null
    }

    fun withDocs(docs: DocumentationBuilder.()-> Unit) {
        docs.invoke(this.docs)
    }

    fun withOptions(parameters: Map<BooleanOption, Any>) {
        this.parameters.putAll(parameters)
    }

    private fun withGradleWrapper(gradleVersion: String = SdkConstants.GRADLE_LATEST_VERSION) {
        val projectPath = "${testProjectDir.root}/${testName.methodName}"
        TestResources.getFile("/gradlew")
            .copyTo(File("${projectPath}/gradlew")).setExecutable(true)
        TestResources.getFile("/gradlew.bat")
            .copyTo(File("${projectPath}/gradlew.bat"))
        TestResources.getFile("/gradle-wrapper.jar")
            .copyTo(File("${projectPath}/gradle/wrapper/gradle-wrapper.jar"))
        File("${projectPath}/gradle/wrapper/gradle-wrapper.properties").writeText(
"""distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https://services.gradle.org/distributions/gradle-${gradleVersion}-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
        """)
    }

    override fun defaultWhen(given: GivenBuilder): BuildResult? {

        val projectDir = File(testProjectDir.root, testName.methodName)
        projectDir.deleteRecursively()
        projectDir.mkdirs()
        given.writeModule(projectDir, sdkLocation())
        docs.index?.apply {
            File(projectDir, "readme.md").writeText(this)
        }

        if (given.tasksToInvoke.isEmpty()) {
            given.tasksToInvoke.add("assembleDebug")
        }

        // create the init script.
        val initScript = privateTestProjectDir.newFile(scriptingLanguage.makeScriptFileName("init.gradle")).also {
            it.writeText(scriptingLanguage.configureInitScript(mavenRepos))
        }
        withGradleWrapper()
        Logger.getGlobal().info(
            """Init script:
            ${scriptingLanguage.configureInitScript(mavenRepos)}""".trim()
        )

        val runnerParameters = mutableListOf<String>()
        parameters.map { "-P${it.key.propertyName}=${it.value}" }.toCollection(runnerParameters)
        runnerParameters.addAll(listOf(
            "-Dorg.gradle.jvmargs=-Xmx2G",
            "--init-script",
            initScript.absolutePath,
            *given.tasksToInvoke.toTypedArray(),
            "--stacktrace"
        ))
        val gradleRunner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(runnerParameters)
            .withEnvironment(mapOf("ANDROID_SDK_ROOT" to sdkLocation()))
            .forwardOutput()

        // if running within Intellij, we must set the Gradle Distribution when invoking the
        // GradleRunner, so fetch it from our workspace and use the same URL we use to build
        // tools/base projects.
        if (System.getenv("SRC")!=null) {
            val srcDir = System.getenv("SRC")
            FileReader(File("$srcDir/tools/gradle/wrapper/gradle-wrapper.properties")).apply {
                val gradleWrapperProperties = Properties()
                    .also { properties -> properties.load(this) }
                val relativePathDistribution = gradleWrapperProperties["distributionUrl"]
                gradleRunner.withGradleDistribution(
                    File("$srcDir/tools/gradle/wrapper/$relativePathDistribution").toURI())
            }
        }
        return if (given.failureExpected) gradleRunner.buildAndFail() else gradleRunner.build()
    }

    override fun instantiateGiven(): GivenBuilder = GivenBuilder(scriptingLanguage, testName)

    protected fun onVariantStats(block : (GradleBuildVariant) -> Unit) {
        val androidProfile =
            File(testProjectDir.root, "${testName.methodName}/build/android-profile")
        Truth.assertThat(androidProfile.exists()).isTrue()
        val listFiles = androidProfile.listFiles().filter { it.name.endsWith(".rawproto") }
        Truth.assertThat(listFiles.size).isEqualTo(1)
        val gradleBuildProfile =
            GradleBuildProfile.parseFrom(Files.readAllBytes(listFiles[0].toPath()))
        Truth.assertThat(gradleBuildProfile).isNotNull()
        Truth.assertThat(gradleBuildProfile.projectList).isNotEmpty()
        gradleBuildProfile.projectList.first {
            it.androidPluginVersion.isNotEmpty()
        }.variantList.forEach { variant ->
            block.invoke(variant)
        }
    }
}
