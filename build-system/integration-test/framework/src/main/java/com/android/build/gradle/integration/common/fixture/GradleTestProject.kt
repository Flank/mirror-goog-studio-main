/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.integration.common.fixture

import com.android.SdkConstants
import com.android.Version
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl.Companion.loadFromFile
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder.MemoryRequirement
import com.android.build.gradle.integration.common.fixture.gradle_project.BuildSystem
import com.android.build.gradle.integration.common.fixture.gradle_project.ProjectLocation
import com.android.build.gradle.integration.common.fixture.gradle_project.initializeProjectLocation
import com.android.build.gradle.integration.common.truth.AarSubject
import com.android.build.gradle.integration.common.truth.forEachLine
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.cxx.configure.ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
import com.android.build.gradle.internal.plugins.VersionCheckPlugin
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.ToolsRevisionUtils
import com.android.builder.model.AndroidProject
import com.android.builder.model.VariantBuildInformation
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.internal.project.ProjectProperties
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.OsType
import com.android.testutils.TestUtils
import com.android.testutils.apk.Aab
import com.android.testutils.apk.Aar
import com.android.testutils.apk.Apk
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.android.utils.Pair
import com.android.utils.combineAsCamelCase
import com.google.common.base.Joiner
import com.google.common.base.MoreObjects
import com.google.common.base.Strings
import com.google.common.base.Throwables
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Lists
import com.google.common.truth.Truth
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.util.GradleVersion
import org.junit.Assert
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Arrays
import java.util.Comparator
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.stream.Collectors

/**
 * JUnit4 test rule for integration test.
 *
 *
 * This rule create a gradle project in a temporary directory. It can be use with the @Rule
 * or @ClassRule annotations. Using this class with @Rule will create a gradle project in separate
 * directories for each unit test, whereas using it with @ClassRule creates a single gradle project.
 *
 *
 * The test directory is always deleted if it already exists at the start of the test to ensure a
 * clean environment.
 */
class GradleTestProject @JvmOverloads internal constructor(
    /** Return the name of the test project.  */
    val name: String = DEFAULT_TEST_PROJECT_NAME,
    private val buildFileName: String,
    private val testProject: TestProject? = null,
    private val targetGradleVersion: String,
    private val withDependencyChecker: Boolean,
    val withConfigurationCaching: BaseGradleExecutor.ConfigurationCaching,
    private val gradleProperties: Collection<String>,
    val heapSize: MemoryRequirement,
    private val compileSdkVersion: String = DEFAULT_COMPILE_SDK_VERSION,
    private val profileDirectory: Path?,
    // CMake's version to be used
    private val cmakeVersion: String?,
    // Indicates if CMake's directory information needs to be saved in local.properties
    private val withCmakeDirInLocalProp: Boolean,
    private val relativeNdkSymlinkPath: String?,
    private val withDeviceProvider: Boolean,
    private val withSdk: Boolean,
    private val withAndroidGradlePlugin: Boolean,
    private val withKotlinGradlePlugin: Boolean,
    private val withPluginManagementBlock: Boolean,
    private val withIncludedBuilds: List<String>,
    private var mutableProjectLocation: ProjectLocation? = null,
    private val repoDirectories: List<Path>?,
    private val additionalMavenRepo: MavenRepoGenerator?,
    val androidSdkDir: File?,
    val androidNdkDir: File,
    private val gradleDistributionDirectory: File,
    private val gradleBuildCacheDirectory: File?,
    val kotlinVersion: String,
    /** Whether or not to output the log of the last build result when a test fails.  */
    private val outputLogOnFailure: Boolean,
    private val openConnections: MutableList<ProjectConnection>? = mutableListOf(),
    /** root project if one exist. This is null for the actual root */
    private val _rootProject: GradleTestProject? = null
) : TestRule {
    companion object {
        const val ENV_CUSTOM_REPO = "CUSTOM_REPO"

        // Limit daemon idle time for tests. 10 seconds is enough for another test
        // to start and reuse the daemon.
        const val GRADLE_DEAMON_IDLE_TIME_IN_SECONDS = 10
        @JvmField
        val DEFAULT_COMPILE_SDK_VERSION: String
        @JvmField
        val DEFAULT_BUILD_TOOL_VERSION: String
        const val DEFAULT_NDK_SIDE_BY_SIDE_VERSION: String = ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
        @JvmField
        val APPLY_DEVICEPOOL_PLUGIN = System.getenv("APPLY_DEVICEPOOL_PLUGIN")?.toBoolean() ?: false
        val USE_LATEST_NIGHTLY_GRADLE_VERSION = System.getenv("USE_GRADLE_NIGHTLY")?.toBoolean() ?: false
        @JvmField
        val GRADLE_TEST_VERSION: String
        private val ANDROID_GRADLE_PLUGIN_VERSION: String?
        const val DEVICE_TEST_TASK = "deviceCheck"

        internal const val MAX_TEST_NAME_DIR_WINDOWS = 50

        /**
         * List of Apk file reference that should be closed and deleted once the TestRule is done. This
         * is useful on Windows when Apk will lock the underlying file and most test code do not use
         * try-with-resources nor explicitly call close().
         */
        private val tmpApkFiles: MutableList<Apk> = mutableListOf()
        private const val COMMON_HEADER = "commonHeader.gradle"
        private const val COMMON_LOCAL_REPO = "commonLocalRepo.gradle"
        private const val COMMON_BUILD_SCRIPT = "commonBuildScript.gradle"
        private const val COMMON_VERSIONS = "commonVersions.gradle"
        const val DEFAULT_TEST_PROJECT_NAME = "project"

        @JvmStatic
        fun builder(): GradleTestProjectBuilder {
            return GradleTestProjectBuilder()
        }

        /** Crawls the tools/external/gradle dir, and gets the latest gradle binary.  */
        private fun computeLatestGradleCheckedIn(): String? {
            val gradleDir = TestUtils.resolveWorkspacePath("tools/external/gradle").toFile()

            // should match gradle-3.4-201612071523+0000-bin.zip, and gradle-3.2-bin.zip
            val gradleVersion = Pattern.compile("^gradle-(\\d+.\\d+)(-.+)?-bin\\.zip$")
            val revisionsCmp: Comparator<Pair<String, String>> =
                Comparator.nullsFirst(
                    Comparator.comparing { it: Pair<String, String> ->
                        GradleVersion.version(it.first)
                    }
                        .thenComparing { obj: Pair<String, String> -> obj.second }
                )
            var highestRevision: Pair<String, String>? = null
            gradleDir.listFiles()?.forEach { f ->
                val matcher = gradleVersion.matcher(f.name)
                if (matcher.matches()) {
                    val current =
                        Pair.of(matcher.group(1), Strings.nullToEmpty(matcher.group(2)))
                    if (revisionsCmp.compare(highestRevision, current) < 0) {
                        highestRevision = current
                    }
                }
            }

            return if (highestRevision == null) {
                null
            } else {
                highestRevision?.first + highestRevision?.second
            }
        }

        private fun generateRepoScript(repositories: List<Path>): String {
            val script = StringBuilder()
            script.append("repositories {\n")
            for (repo in repositories) {
                script.append(mavenSnippet(repo))
            }
            script.append("}\n")
            return script.toString()
        }

        fun mavenSnippet(repo: Path): String {
            return String.format(
                """maven {
  url '%s'
  metadataSources {
    mavenPom()
    artifact()
  }
 }
""",
                repo.toUri().toString()
            )
        }

        @JvmStatic
        val localRepositories: List<Path>
            get() = BuildSystem.get().localRepositories

        /**
         * Returns the prebuilts CMake folder for the requested version of CMake. Note: This function
         * returns a path within the Android SDK which is expected to be used in cmake.dir.
         */
        @JvmStatic
        fun getCmakeVersionFolder(cmakeVersion: String): File {
            val cmakeVersionFolderInSdk =
                    TestUtils.getSdk().resolve(String.format("cmake/%s", cmakeVersion))
            if (!Files.isDirectory(cmakeVersionFolderInSdk)) {
                throw RuntimeException(
                    String.format("Could not find CMake in %s", cmakeVersionFolderInSdk)
                )
            }
            return cmakeVersionFolderInSdk.toFile()
        }

        /**
         * The ninja in 3.6 cmake folder does not support long file paths. This function returns the
         * version that does handle them.
         */
        val preferredNinja: File
            get() {
                val cmakeFolder = getCmakeVersionFolder("3.10.4819442")
                return if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
                    File(cmakeFolder, "bin/ninja.exe")
                } else {
                    File(cmakeFolder, "bin/ninja")
                }
            }

        private fun generateVersions(): String {
            return String.format(
                Locale.US,
                "// Generated by GradleTestProject::generateVersions%n"
                        + "buildVersion = '%s'%n"
                        + "baseVersion = '%s'%n"
                        + "supportLibVersion = '%s'%n"
                        + "testSupportLibVersion = '%s'%n"
                        + "playServicesVersion = '%s'%n"
                        + "supportLibMinSdk = %d%n"
                        + "ndk19SupportLibMinSdk = %d%n"
                        + "constraintLayoutVersion = '%s'%n",
                Version.ANDROID_GRADLE_PLUGIN_VERSION,
                Version.ANDROID_TOOLS_BASE_VERSION,
                SUPPORT_LIB_VERSION,
                TEST_SUPPORT_LIB_VERSION,
                PLAY_SERVICES_VERSION,
                SUPPORT_LIB_MIN_SDK,
                NDK_19_SUPPORT_LIB_MIN_SDK,
                SdkConstants.LATEST_CONSTRAINT_LAYOUT_VERSION
            )
        }

        /** Returns a string that contains the gradle buildscript content  */
        @JvmStatic
        val gradleBuildscript: String
            get() =
                """
                apply from: "../commonHeader.gradle"
                buildscript { apply from: "../commonBuildScript.gradle" }
                apply from: "../commonLocalRepo.gradle"

                // Treat javac warnings as errors
                tasks.withType(JavaCompile) {
                    options.compilerArgs << "-Werror"
                }
                """.trimIndent()

        @JvmStatic
        val compileSdkHash: String
            get() {
                var compileTarget = DEFAULT_COMPILE_SDK_VERSION.replace("[\"']".toRegex(), "")
                if (!compileTarget.startsWith("android-")) {
                    compileTarget = "android-$compileTarget"
                }
                return compileTarget
            }

        init {
            try {
                GRADLE_TEST_VERSION = if (USE_LATEST_NIGHTLY_GRADLE_VERSION) {
                    computeLatestGradleCheckedIn() ?: error("Failed to find latest nightly version.")
                } else {
                    VersionCheckPlugin.GRADLE_MIN_VERSION.toString()
                }

                // These are some properties that we use in the integration test projects, when generating
                // build.gradle files. In case you would like to change any of the parameters, for instance
                // when testing cross product of versions of buildtools, compile sdks, plugin versions,
                // there are corresponding system environment variable that you are able to set.
                val envBuildToolVersion = Strings.emptyToNull(System.getenv("CUSTOM_BUILDTOOLS"))
                DEFAULT_BUILD_TOOL_VERSION =
                    MoreObjects.firstNonNull(
                        envBuildToolVersion,
                        ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION.toString()
                    )
                val envVersion = Strings.emptyToNull(System.getenv("CUSTOM_PLUGIN_VERSION"))
                ANDROID_GRADLE_PLUGIN_VERSION =
                    MoreObjects.firstNonNull(
                        envVersion,
                        Version.ANDROID_GRADLE_PLUGIN_VERSION
                    )
                val envCustomCompileSdk = Strings.emptyToNull(System.getenv("CUSTOM_COMPILE_SDK"))
                DEFAULT_COMPILE_SDK_VERSION =
                    MoreObjects.firstNonNull(
                        envCustomCompileSdk,
                        SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString()
                    )
            } catch (t: Throwable) {
                // Print something to stdout, to give us a chance to debug initialization problems.
                println(Throwables.getStackTraceAsString(t))
                throw Throwables.propagate(t)
            }
        }
    }


    private val ndkSymlinkPath: File? by lazy {
        relativeNdkSymlinkPath?.let { location.testLocation.buildDir.resolve(it).canonicalFile }
    }

    val androidNdkSxSRootSymlink: File?
        get() = ndkSymlinkPath?.resolve(SdkConstants.FD_NDK_SIDE_BY_SIDE)

    val location: ProjectLocation
        get() = mutableProjectLocation ?: error("Project location has not been initialized yet")

    val buildFile: File
        get() = File(location.projectDir, buildFileName)

    val projectDir: File
        get() = location.projectDir

    lateinit var localProp: File
        private set

    /** Returns a path to NDK suitable for embedding in build.gradle. It has slashes escaped for Windows */
    val ndkPath: String
        get() = androidNdkDir.absolutePath.replace("\\", "\\\\")

    private var additionalMavenRepoDir: Path? = null

    /** \Returns the latest build result.  */
    private var _buildResult: GradleBuildResult? = null

    /** Returns the latest build result.  */
    val buildResult: GradleBuildResult
        get() = _buildResult ?: throw RuntimeException("No result available. Run Gradle first.")

    /** Returns a Gradle project Connection  */
    private val projectConnection: ProjectConnection by lazy {

        val connector = GradleConnector.newConnector()
        (connector as DefaultGradleConnector)
            .daemonMaxIdleTime(
                GRADLE_DEAMON_IDLE_TIME_IN_SECONDS,
                TimeUnit.SECONDS
            )
        val distributionName = String.format("gradle-%s-bin.zip", targetGradleVersion)
        val distributionZip =
            File(gradleDistributionDirectory, distributionName)
        assertThat(distributionZip).isFile()
        val connection = connector
            .useDistribution(distributionZip.toURI())
            .useGradleUserHomeDir(location.testLocation.gradleUserHome.toFile())
            .forProjectDirectory(location.projectDir)
            .connect()
        rootProject.openConnections?.add(connection)

        connection
    }

    /**
     * Create a GradleTestProject representing a subProject of another GradleTestProject.
     *
     * @param subProject name of the subProject, or the subProject's gradle project path
     * @param rootProject root GradleTestProject.
     */
    private constructor(
        subProject: String,
        rootProject: GradleTestProject
    ) :
        this(
            name = subProject.substring(subProject.lastIndexOf(':') + 1),
            buildFileName = rootProject.buildFileName, // FIXME this is problematic because that may not be what we want.
            testProject = null,
            targetGradleVersion = rootProject.targetGradleVersion,
            withDependencyChecker = rootProject.withDependencyChecker,
            withConfigurationCaching = rootProject.withConfigurationCaching,
            gradleProperties = ImmutableList.of(),
            heapSize = rootProject.heapSize,
            compileSdkVersion = rootProject.compileSdkVersion,
            profileDirectory = rootProject.profileDirectory,
            cmakeVersion = rootProject.cmakeVersion,
            withCmakeDirInLocalProp = rootProject.withCmakeDirInLocalProp,
            relativeNdkSymlinkPath = rootProject.relativeNdkSymlinkPath,
            withDeviceProvider = rootProject.withDeviceProvider,
            withSdk = rootProject.withSdk,
            withAndroidGradlePlugin = rootProject.withAndroidGradlePlugin,
            withKotlinGradlePlugin = rootProject.withKotlinGradlePlugin,
            withPluginManagementBlock = rootProject.withPluginManagementBlock,
            withIncludedBuilds = ImmutableList.of(),
            mutableProjectLocation = rootProject.location.createSubProjectLocation(subProject),
            repoDirectories = rootProject.repoDirectories,
            additionalMavenRepo = rootProject.additionalMavenRepo,
            androidSdkDir = rootProject.androidSdkDir,
            androidNdkDir = rootProject.androidNdkDir,
            gradleDistributionDirectory = rootProject.gradleDistributionDirectory,
            gradleBuildCacheDirectory = rootProject.gradleBuildCacheDirectory,
            kotlinVersion = rootProject.kotlinVersion,
            outputLogOnFailure = rootProject.outputLogOnFailure,
            openConnections = null,
            _rootProject = rootProject
        ) {

        Assert.assertTrue(
            "No subproject dir at $projectDir",
            projectDir.isDirectory
        )
    }

    /** returns the root project or this if there's no root */
    val rootProject: GradleTestProject
        get() = _rootProject ?: this

    override fun apply(
        base: Statement,
        description: Description
    ): Statement {
        return if (rootProject != this) {
            rootProject.apply(base, description)
        } else object : Statement() {
            override fun evaluate() {
                if (mutableProjectLocation == null) {
                    mutableProjectLocation = initializeProjectLocation(
                        description.testClass,
                        description.methodName,
                        name
                    )
                }
                populateTestDirectory()
                var testFailed = false
                try {
                    base.evaluate()
                } catch (e: Throwable) {
                    testFailed = true
                    throw e
                } finally {
                    for (tmpApkFile in tmpApkFiles) {
                        try {
                            tmpApkFile.close()
                        } catch (e: Exception) {
                            System.err
                                .println("Error while closing APK file : " + e.message)
                        }
                        val tmpFile = tmpApkFile.file.toFile()
                        if (tmpFile.exists() && !tmpFile.delete()) {
                            System.err.println(
                                "Cannot delete temporary file " + tmpApkFile.file
                            )
                        }
                    }
                    openConnections?.forEach(ProjectConnection::close)

                    if (outputLogOnFailure && testFailed) {
                        _buildResult?.let {
                            System.err
                                .println("==============================================")
                            System.err
                                .println("= Test $description failed. Last build:")
                            System.err
                                .println("==============================================")
                            System.err
                                .println("=================== Stderr ===================")
                            // All output produced during build execution is written to the standard
                            // output file handle since Gradle 4.7. This should be empty.
                            it.stderr.forEachLine { System.err.println(it) }
                            System.err
                                .println("=================== Stdout ===================")
                            it.stdout.forEachLine { System.err.println(it) }
                            System.err
                                .println("==============================================")
                            System.err
                                .println("=============== End last build ===============")
                            System.err
                                .println("==============================================")
                        }
                    }
                }
            }
        }
    }

    private fun populateTestDirectory() {
        val projectDir = projectDir
        FileUtils.deleteRecursivelyIfExists(projectDir)
        FileUtils.mkdirs(projectDir)

        val projectParentDir = projectDir.parent
        File(projectParentDir, COMMON_VERSIONS).writeText(generateVersions())
        File(projectParentDir, COMMON_LOCAL_REPO).writeText(generateProjectRepoScript())
        File(projectParentDir, COMMON_HEADER).writeText(generateCommonHeader())
        File(projectParentDir, COMMON_BUILD_SCRIPT).writeText(generateCommonBuildScript())

        if (testProject != null) {
            testProject.write(
                projectDir,
                if (testProject.containsFullBuildScript()) "" else gradleBuildscript
            )
        } else {
            buildFile.writeText(gradleBuildscript)
        }
        createSettingsFile()
        localProp = createLocalProp()
        createGradleProp()
    }

    private fun getRepoDirectories(): List<Path> {
        return if (repoDirectories != null) {
            repoDirectories
        } else {
            val builder =
                ImmutableList.builder<Path>()
            builder.addAll(localRepositories)
            val additionalMavenRepo = getAdditionalMavenRepo()
            if (additionalMavenRepo != null) {
                builder.add(additionalMavenRepo)
            }
            builder.build()
        }
    }

    // Not enabled in tests
    val booleanOptions: Map<BooleanOption, Boolean>
        get() {
            val builder =
                ImmutableMap
                    .builder<BooleanOption, Boolean>()
            builder.put(
                BooleanOption
                    .DISALLOW_DEPENDENCY_RESOLUTION_AT_CONFIGURATION,
                withDependencyChecker
            )
            builder.put(
                BooleanOption.ENABLE_SDK_DOWNLOAD,
                false
            ) // Not enabled in tests
            return builder.build()
        }

    private fun generateProjectRepoScript(): String {
        return generateRepoScript(getRepoDirectories())
    }

    private fun getAdditionalMavenRepo(): Path? {
        if (additionalMavenRepo == null) {
            return null
        }
        if (additionalMavenRepoDir == null) {
            val moreMavenRepoDir = projectDir
                .toPath()
                .parent
                .resolve("additional_maven_repo")
            additionalMavenRepoDir = moreMavenRepoDir
            additionalMavenRepo.generate(moreMavenRepoDir)
        }
        return additionalMavenRepoDir
    }

    private fun generateCommonHeader(): String {
        var result = String.format(
            """
ext {
    buildToolsVersion = '%1${"$"}s'
    latestCompileSdk = %2${"$"}s
    kotlinVersion = '%4${"$"}s'
}
allprojects {
    ${generateProjectRepoScript()}
}
""",
            DEFAULT_BUILD_TOOL_VERSION,
            compileSdkVersion,
            false,
            kotlinVersion
        )
        if (APPLY_DEVICEPOOL_PLUGIN) {
            result += """
allprojects { proj ->
    proj.plugins.withId('com.android.application') {
        proj.apply plugin: 'devicepool'
    }
    proj.plugins.withId('com.android.library') {
        proj.apply plugin: 'devicepool'
    }
    proj.plugins.withId('com.android.model.application') {
        proj.apply plugin: 'devicepool'
    }
    proj.plugins.withId('com.android.model.library') {
        proj.apply plugin: 'devicepool'
    }
}
"""
        }
        return result
    }

    fun generateCommonBuildScript(): String {
        return BuildSystem.get()
            .getCommonBuildScriptContent(
                withAndroidGradlePlugin, withKotlinGradlePlugin, withDeviceProvider
            )
    }

    /**
     * Create a GradleTestProject representing a subproject.
     *
     * @param name name of the subProject, or the subProject's gradle project path
     */
    fun getSubproject(name: String): GradleTestProject {
        return GradleTestProject(name, rootProject)
    }

    /** Return the path to the default Java main source dir.  */
    val mainSrcDir: File
        get() = getMainSrcDir("java")

    /** Return the path to the default Java main source dir.  */
    fun getMainSrcDir(language: String): File {
        return FileUtils.join(projectDir, "src", "main", language)
    }

    /** Return the path to the default Java main resources dir.  */
    val mainJavaResDir: File
        get() = FileUtils.join(projectDir, "src", "main", "resources")

    /** Return the path to the default main jniLibs dir.  */
    val mainJniLibsDir: File
        get() = FileUtils.join(projectDir, "src", "main", "jniLibs")

    /** Return the path to the default main res dir.  */
    val mainResDir: File
        get() = FileUtils.join(projectDir, "src", "main", "res")

    /** Return the settings.gradle of the test project.  */
    val settingsFile: File
        get() = File(projectDir, "settings.gradle")

    /** Return the gradle.properties file of the test project.  */
    val gradlePropertiesFile: File
        get() = File(projectDir, "gradle.properties")

    val buildDir: File
        get() = FileUtils.join(projectDir, "build")

    /** Return the output directory from Android plugins.  */
    val outputDir: File
        get() = FileUtils.join(projectDir, "build", AndroidProject.FD_OUTPUTS)

    /** Return the output directory from Android plugins.  */
    val intermediatesDir: File
        get() = FileUtils
            .join(projectDir, "build", AndroidProject.FD_INTERMEDIATES)

    /** Return a File under the output directory from Android plugins.  */
    fun getOutputFile(vararg paths: String?): File {
        return FileUtils.join(outputDir, *paths)
    }

    /** Return a File under the intermediates directory from Android plugins.  */
    fun getIntermediateFile(vararg paths: String?): File {
        return FileUtils.join(intermediatesDir, *paths)
    }

    /** Returns a File under the generated folder.  */
    fun getGeneratedSourceFile(vararg paths: String?): File {
        return FileUtils.join(generatedDir, *paths)
    }

    val generatedDir: File
        get() = FileUtils.join(projectDir, "build", AndroidProject.FD_GENERATED)

    /**
     * Returns the directory in which profiles will be generated. A null value indicates that
     * profiles may not be generated, though setting [ ][com.android.build.gradle.options.StringOption.PROFILE_OUTPUT_DIR] in gradle.properties will
     * induce profile generation without affecting this return value
     */
    fun getProfileDirectory(): Path? {
        return if (profileDirectory == null || profileDirectory.isAbsolute) {
            profileDirectory
        } else {
            rootProject.projectDir.toPath().resolve(profileDirectory)
        }
    }

    /**
     * Return the output apk File from the application plugin for the given dimension.
     *
     *
     * Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     *
     */
    @Deprecated(
        """Use {@link #getApk(ApkType, String...)} or {@link #getApk(String, ApkType,
     *     String...)}"""
    )
    fun getApk(vararg dimensions: String?): Apk {
        val dimensionList: MutableList<String?> =
            Lists
                .newArrayListWithExpectedSize(1 + dimensions.size)
        dimensionList.add(name)
        dimensionList.addAll(Arrays.asList(*dimensions))
        // FIX ME : "debug" should be an explicit variant name rather than mixed in dimensions.
        val flavorDimensionList =
            Arrays.stream(dimensions)
                .filter { dimension: String? -> dimension != "unsigned" }
                .collect(
                    Collectors.toList()
                )
        val apkFile = getOutputFile(
            "apk"
                    + File.separatorChar
                    + Joiner.on(File.separatorChar)
                .join(flavorDimensionList)
                    + File.separatorChar
                    + Joiner.on("-").join(dimensionList)
                    + SdkConstants.DOT_ANDROID_PACKAGE
        )
        return _getApk(apkFile)
    }

    /**
     * Internal Apk construction facility that will copy the file first on Windows to avoid locking
     * the underlying file.
     *
     * @param apkFile the file handle to create the APK from.
     * @return the Apk object.
     */
    private fun _getApk(apkFile: File): Apk {
        val apk: Apk
        if (OsType.getHostOs() == OsType.WINDOWS && apkFile.exists()) {
            val copy = File.createTempFile("tmp", ".apk")
            FileUtils.copyFile(apkFile, copy)
            apk = object : Apk(copy) {
                override fun getFile(): Path {
                    return apkFile.toPath()
                }
            }
            tmpApkFiles.add(apk)
        } else {
            // the IDE erroneously indicate to use try-with-resources because APK is a autocloseable
            // but nothing is opened here.
            apk = Apk(apkFile)
        }
        return apk
    }

    public interface ApkType {
        val buildType: String
        val testName: String?
        val isSigned: Boolean

        companion object {
            @JvmStatic
            fun of(
                name: String,
                isSigned: Boolean
            ): ApkType {
                return object :
                    ApkType {
                    override val buildType: String
                        get() = name

                    override val testName: String?
                        get() = null

                    override val isSigned: Boolean
                        get() = isSigned

                    override fun toString(): String {
                        return MoreObjects.toStringHelper(this)
                            .add("getBuildType", buildType)
                            .add("getTestName", testName)
                            .add("isSigned", isSigned)
                            .toString()
                    }
                }
            }

            @JvmStatic
            fun of(
                name: String,
                testName: String?,
                isSigned: Boolean
            ): ApkType {
                return object :
                    ApkType {
                    override val buildType: String
                        get() = name

                    override val testName: String?
                        get() = testName

                    override val isSigned: Boolean
                        get() = isSigned

                    override fun toString(): String {
                        return MoreObjects.toStringHelper(this)
                            .add("getBuildType", buildType)
                            .add("getTestName", testName)
                            .add("isSigned", isSigned)
                            .toString()
                    }
                }
            }

            @JvmField
            val DEBUG = of("debug", true)
            @JvmField
            val RELEASE = of("release", false)
            @JvmField
            val RELEASE_SIGNED = of("release", true)
            @JvmField
            val ANDROIDTEST_DEBUG = of("debug", "androidTest", true)
            @JvmField
            val ANDROIDTEST_RELEASE = of("release", "androidTest", true)
            @JvmField
            val MIN_SIZE_REL = of("minSizeRel", false)
        }
    }

    /**
     * Return the output apk File from the application plugin for the given dimension.
     *
     *
     * Expected dimensions orders are: - product flavors -
     */
    fun getApk(apk: ApkType, vararg dimensions: String): Apk {
        return getApk(null /* filterName */, apk, *dimensions)
    }

    /**
     * Return the bundle universal output apk File from the application plugin for the given
     * dimension.
     *
     *
     * Expected dimensions orders are: - product flavors -
     */
    fun getBundleUniversalApk(apk: ApkType): Apk {
        return getOutputApk(
            "universal_apk",
            null,
            apk,
            ImmutableList.of(),
            "universal"
        )
    }

    /**
     * Return the output full split apk File from the application plugin for the given dimension.
     *
     *
     * Expected dimensions orders are: - product flavors -
     */
    fun getApk(
        filterName: String?,
        apkType: ApkType,
        vararg dimensions: String
    ): Apk {
        return getOutputApk(
            "apk",
            filterName,
            apkType,
            ImmutableList.copyOf(dimensions),
            null
        )
    }

    private fun getOutputApk(
        pathPrefix: String,
        filterName: String?,
        apkType: ApkType,
        dimensions: ImmutableList<String>,
        suffix: String?
    ): Apk {
        return _getApk(
            getOutputFile(
                pathPrefix
                        + (if (apkType.testName != null) File.separatorChar
                    .toString() + apkType.testName else "")
                        + File.separatorChar
                        + dimensions.combineAsCamelCase()
                        + File.separatorChar
                        + apkType.buildType
                        + File.separatorChar
                        + mangleApkName(apkType, filterName, dimensions, suffix)
                        + if (apkType.isSigned) SdkConstants
                    .DOT_ANDROID_PACKAGE else "-unsigned" + SdkConstants
                    .DOT_ANDROID_PACKAGE
            )
        )
    }

    /** Returns the APK given its file name.  */
    fun getApkByFileName(apkType: ApkType, apkFileName: String): Apk {
        return _getApk(
            getOutputFile(
                "apk"
                        + (if (apkType.testName != null) File.separatorChar.toString() + apkType.testName else "")
                        + File.separatorChar
                        + apkType.buildType
                        + File.separatorChar
                        + apkFileName
            )
        )
    }

    fun getBundle(type: ApkType): Aab {
        val bundles =
            outputDir.resolve("bundle/${type.buildType}/")
                .walk()
                .filter { it.extension == SdkConstants.EXT_APP_BUNDLE }
                .toList()
        if (bundles.size > 1) {
            throw UnsupportedOperationException("Support for multiple bundles is not implemented.")
        }
        return Aab(bundles.single())
    }

    private fun mangleApkName(
        apkType: ApkType,
        filterName: String?,
        dimensions: List<String?>,
        suffix: String?
    ): String {
        val dimensionList: MutableList<String?> =
            Lists
                .newArrayListWithExpectedSize(1 + dimensions.size)
        dimensionList.add(name)
        dimensionList.addAll(dimensions)
        if (!Strings.isNullOrEmpty(filterName)) {
            dimensionList.add(filterName)
        }
        if (!Strings.isNullOrEmpty(apkType.buildType)) {
            dimensionList.add(apkType.buildType)
        }
        if (!Strings.isNullOrEmpty(apkType.testName)) {
            dimensionList.add(apkType.testName)
        }
        if (suffix != null) {
            dimensionList.add(suffix)
        }
        return Joiner.on("-").join(dimensionList)
    }

    val testApk: Apk
        get() = getApk(ApkType.ANDROIDTEST_DEBUG)

    fun getTestApk(vararg dimensions: String): Apk {
        return getApk(ApkType.ANDROIDTEST_DEBUG, *dimensions)
    }

    private fun testAar(
        dimensions: List<String>,
        action: AarSubject.() -> Unit
    ) {
        val dimensionList: MutableList<String?> =
            Lists.newArrayListWithExpectedSize(1 + dimensions.size)
        dimensionList.add(name)
        dimensionList.addAll(dimensions)
        Aar(
            getOutputFile(
                "aar",
                Joiner.on("-").join(dimensionList) + SdkConstants
                    .DOT_AAR
            )
        ).use { aar ->
            val subject =
                Truth.assertAbout(AarSubject.aars()).that(aar)
            action(subject)
        }
    }

    /**
     * Allows testing the aar.
     *
     * Testing happens in the callback that receives an [AarSubject]
     *
     * Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    fun testAar(
        dimension1: String,
        action: Consumer<AarSubject>
    ) {
        testAar(listOf(dimension1)) { action.accept(this) }
    }

    /**
     * Allows testing the aar.
     *
     * Testing happens in the callback that receives an [AarSubject]
     *
     * Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    fun testAar(
        dimension1: String,
        dimension2: String,
        action: Consumer<AarSubject>
    ) {
        testAar(listOf(dimension1, dimension2)) { action.accept(this) }
    }

    /**
     * Allows testing the aar.
     *
     * Testing happens in the callback that receives an [AarSubject]
     *
     * Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    fun assertThatAar(
        dimension1: String,
        action: AarSubject.() -> Unit
    ) {
        testAar(listOf(dimension1), action)
    }

    /**
     * Allows testing the aar.
     *
     * Testing happens in the callback that receives an [AarSubject]
     *
     * Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    fun assertThatAar(
        dimension1: String,
        dimension2: String,
        action: AarSubject.() -> Unit
    ) {
        testAar(listOf(dimension1, dimension2), action)
    }

    private fun getAar(
        dimensions: List<String>,
        action: Aar.() -> Unit
    ) {
        val dimensionList: MutableList<String?> =
            Lists.newArrayListWithExpectedSize(1 + dimensions.size)
        dimensionList.add(name)
        dimensionList.addAll(dimensions)
        Aar(
            getOutputFile(
                "aar",
                Joiner.on("-").join(dimensionList) + SdkConstants.DOT_AAR
            )
        ).use { aar -> action(aar) }
    }

    /**
     * Allows testing the aar.
     *
     * Testing happens in the callback that receives an [AarSubject]
     *
     * Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    fun getAar(
        dimension1: String,
        action: Consumer<Aar>
    ) {
        getAar(listOf(dimension1)) { action.accept(this) }
    }

    /**
     * Allows testing the aar.
     *
     * Testing happens in the callback that receives an [AarSubject]
     *
     * Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    fun withAar(
        dimension1: String,
        action: Aar.() -> Unit
    ) {
        getAar(listOf(dimension1), action)
    }

    /**
     * Allows testing the aar.
     *
     * Testing happens in the callback that receives an [AarSubject]
     *
     * Expected dimensions orders are: - product flavors - build type - other modifiers (e.g.
     * "unsigned", "aligned")
     */
    fun withAar(
        dimensions: List<String>,
        action: Aar.() -> Unit
    ) {
        getAar(dimensions, action)
    }

    /**
     * Returns the output bundle file from the instantapp plugin for the given dimension.
     *
     *
     * Expected dimensions orders are: - product flavors - build type
     */
    fun getInstantAppBundle(vararg dimensions: String): Zip {
        val dimensionList: MutableList<String?> =
            Lists
                .newArrayListWithExpectedSize(1 + dimensions.size)
        dimensionList.add(name)
        dimensionList.addAll(Arrays.asList(*dimensions))
        return Zip(
            getOutputFile(
                "apk",
                ImmutableList.copyOf(dimensions)
                    .combineAsCamelCase(),
                Joiner.on("-").join(dimensionList) + SdkConstants
                    .DOT_ZIP
            )
        )
    }

    /** Fluent method to run a build.  */
    fun executor(): GradleTaskExecutor {
        return applyOptions(GradleTaskExecutor(this, projectConnection))
    }

    /** Fluent method to get the model.  */
    fun model(): ModelBuilder {
        return applyOptions(ModelBuilder(this, projectConnection))
    }

    /** Fluent method to get the model.  */
    fun modelV2(): ModelBuilderV2 {
        return applyOptions(ModelBuilderV2(this, projectConnection)).withPerTestPrefsRoot()
    }

    private fun <T : BaseGradleExecutor<T>> applyOptions(executor: T): T {
        for ((option, value) in booleanOptions) {
            executor.with(option, value)
        }

        for (option in booleanOptions.keys) {
            executor.suppressOptionWarning(option)
        }
        return executor
    }

    /**
     * Runs gradle on the project. Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     */
    fun execute(vararg tasks: String) {
        _buildResult = executor().run(*tasks)
    }

    fun execute(
        arguments: List<String>,
        vararg tasks: String
    ) {
        _buildResult = executor().withArguments(arguments).run(*tasks)
    }

    fun executeExpectingFailure(vararg tasks: String): GradleConnectionException? {
        return executor().expectFailure().run(*tasks).run {
            _buildResult = this
            exception
        }
    }

    /**
     * Runs gradle on the project, and returns the project model. Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     * @return the AndroidProject model for the project.
     */
    fun executeAndReturnModel(vararg tasks: String): ModelContainer<AndroidProject> {
        _buildResult = executor().run(*tasks)
        return model().fetchAndroidProjects()
    }

    /**
     * Runs gradle on the project, and returns the model of the specified type. Throws exception on
     * failure.
     *
     * @param modelClass Class of the model to return
     * @param tasks Variadic list of tasks to execute.
     * @return the model for the project with the specified type.
     */
    fun <T> executeAndReturnModel(
        modelClass: Class<T>,
        vararg tasks: String
    ): T {
        _buildResult = executor().run(*tasks)
        return model().fetch(modelClass)
    }

    /**
     * Runs gradle on the project, and returns the (minimal) output model. Throws exception on
     * failure.
     *
     * @param tasks Variadic list of tasks to execute.
     * @return the output models for the project as map of output model name (variant name +
     * artifact name) to the associated [BuiltArtifacts]
     */
    fun executeAndReturnOutputModels(vararg tasks: String): Map<String, BuiltArtifacts> {
        executor().run(*tasks)
        val androidProjectModelContainer = model().ignoreSyncIssues().fetchAndroidProjects()
        val onlyModel = androidProjectModelContainer.onlyModel
        val mapOfVariantOutputs = ImmutableMap.builder<String, BuiltArtifacts>()
        for (variant in onlyModel.variants) {
            val postModelFile = variant.mainArtifact.assembleTaskOutputListingFile
            val builtArtifacts: BuiltArtifacts? = loadFromFile(
                File(postModelFile),
                File(postModelFile).parentFile.toPath()
            )
            if (builtArtifacts != null) {
                mapOfVariantOutputs.put(variant.name, builtArtifacts)
            }
            for (extraAndroidArtifact in variant.extraAndroidArtifacts) {
                val extraModelFile = extraAndroidArtifact.assembleTaskOutputListingFile
                if (!extraModelFile.isEmpty()) {
                    val extraBuiltArtifacts: BuiltArtifacts? =
                        loadFromFile(
                            File(postModelFile),
                            File(postModelFile).parentFile.toPath()
                        )
                    if (extraBuiltArtifacts != null) {
                        mapOfVariantOutputs.put(
                            variant.name + extraAndroidArtifact.name,
                            extraBuiltArtifacts
                        )
                    }
                }
            }
        }
        return mapOfVariantOutputs.build()
    }

    /**
     * Runs gradle on the project, and returns a project model for each sub-project. Throws
     * exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     * @return the AndroidProject model for the project.
     */
    fun executeAndReturnMultiModel(vararg tasks: String): ModelContainer<AndroidProject> {
        _buildResult = executor().run(*tasks)
        return model().fetchAndroidProjects()
    }

    /**
     * Runs gradle on the project, and returns the model of the specified type for each sub-project.
     * Throws exception on failure.
     *
     * @param modelClass Class of the model to return
     * @param tasks Variadic list of tasks to execute.
     * @return map of project names to output models
     */
    fun <T> executeAndReturnMultiModel(
        modelClass: Class<T>,
        vararg tasks: String?
    ): Map<String, T> {
        _buildResult = executor().run(*tasks)
        return model().fetchMulti(modelClass)
    }

    internal class ProjectOutputModel(val buildInformationByVariantName: Map<String, VariantBuildInformation>)

    fun setLastBuildResult(lastBuildResult: GradleBuildResult) {
        _buildResult = lastBuildResult
    }

    /**
     * Create a File object. getTestDir will be the base directory if a relative path is supplied.
     *
     * @param path Full path of the file. May be a relative path.
     */
    fun file(path: String): File {
        val result = File(FileUtils.toSystemDependentPath(path))
        return if (result.isAbsolute) {
            result
        } else {
            File(projectDir, path)
        }
    }

    private fun createLocalProp(): File {
        val mainLocalProp = createLocalProp(projectDir)
        for (includedBuild in withIncludedBuilds) {
            createLocalProp(File(projectDir, includedBuild))
        }
        return mainLocalProp
    }

    private fun createLocalProp(destDir: File): File {
        val localProp = ProjectPropertiesWorkingCopy.create(
            destDir.absolutePath, ProjectPropertiesWorkingCopy.PropertyType.LOCAL
        )
        if (withSdk) {
            val androidSdkDir = this.androidSdkDir
                ?: throw RuntimeException("androidHome is null while withSdk is true")
            localProp.setProperty(ProjectProperties.PROPERTY_SDK, androidSdkDir.absolutePath)
        }

        if (withCmakeDirInLocalProp && cmakeVersion != null && cmakeVersion.isNotEmpty()) {
            localProp.setProperty(
                ProjectProperties.PROPERTY_CMAKE,
                getCmakeVersionFolder(cmakeVersion).absolutePath
            )
        }
        ndkSymlinkPath?.let {
            localProp.setProperty(ProjectProperties.PROPERTY_NDK_SYMLINKDIR, it.absolutePath)
        }

        localProp.save()
        return localProp.file as File
    }

    private fun createSettingsFile() {
        var settingsContent = if (settingsFile.exists()) settingsFile.readText() else ""

        if (withPluginManagementBlock) {
            settingsContent = """
            pluginManagement { t ->
                apply from: "../commonLocalRepo.gradle", to: t

                resolutionStrategy {
                    eachPlugin {
                        if(requested.id.namespace == "com.android") {
                            useModule("com.android.tools.build:gradle:$ANDROID_GRADLE_PLUGIN_VERSION")
                        }
                    }
                }
            }

        """.trimIndent() + settingsContent
        }

        if (gradleBuildCacheDirectory != null) {
            val absoluteFile: File = if (gradleBuildCacheDirectory.isAbsolute)
                gradleBuildCacheDirectory
            else
                File(projectDir, gradleBuildCacheDirectory.path)
            settingsContent +=
                """
buildCache {
    local {
        directory = "${absoluteFile.path.replace("\\", "\\\\")}"
    }
}
"""
        }

        if (settingsContent.isNotEmpty()) {
            settingsFile.writeText(settingsContent)
        }
    }

    private fun createGradleProp() {
        if (gradleProperties.isEmpty()) {
            return
        }

        gradlePropertiesFile.appendText(
            gradleProperties.joinToString(separator = System.lineSeparator(), prefix = System.lineSeparator(), postfix = System.lineSeparator())
        )
    }

    /**
     * Adds `android.useAndroidX=true` to the gradle.properties file (for projects that use AndroidX
     * dependencies, see bug 130286699).
     */
    fun addUseAndroidXProperty() {
        TestFileUtils.appendToFile(
            gradlePropertiesFile,
            BooleanOption.USE_ANDROID_X.propertyName + "=true"
        )
    }

    /**
     * Adds an adb timeout to the root project build file and applies it to all subprojects, so that
     * tests using adb will fail fast when there is no response.
     */
    @JvmOverloads
    fun addAdbTimeout(timeout: Duration = Duration.ofSeconds(30)) {
        TestFileUtils.appendToFile(
                buildFile,
                """
                allprojects { proj ->
                    proj.plugins.withId('com.android.application') {
                        android.adbOptions.timeOutInMs ${timeout.toMillis()}
                    }
                    proj.plugins.withId('com.android.library') {
                        android.adbOptions.timeOutInMs ${timeout.toMillis()}
                    }
                    proj.plugins.withId('com.android.dynamic-feature') {
                        android.adbOptions.timeOutInMs ${timeout.toMillis()}
                    }
                }
                """.trimIndent()
        )
    }
}
