
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

import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.google.common.truth.Truth
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull

class KotlinScriptApiTests: VariantApiBaseTest(TestType.Script) {
    private val testingElements= TestingElements(scriptingLanguage)
    @Test
    fun getApksTest() {
        given {
            tasksToInvoke.add(":app:debugDisplayApks")
            addModule(":app") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
            // language=kotlin
            """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.DirectoryProperty
            import org.gradle.api.tasks.InputFiles
            import org.gradle.api.tasks.TaskAction

            import com.android.build.api.variant.BuiltArtifactsLoader
            import com.android.build.api.artifact.ArtifactType
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Internal

            ${testingElements.getDisplayApksTask()}
            android {
                ${testingElements.addCommonAndroidBuildLogic()}

                onVariantProperties {
                    project.tasks.register<DisplayApksTask>("${ '$' }{name}DisplayApks") {
                        apkFolder.set(artifacts.get(ArtifactType.APK))
                        builtArtifactsLoader.set(artifacts.getBuiltArtifactsLoader())
                    }
                }
            }
        """.trimIndent()
                testingElements.addManifest( this)
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# artifacts.get in Kotlin

This sample show how to obtain a built artifact from the AGP. The built artifact is identified by
its [ArtifactType] and in this case, it's [ArtifactType.APK].
The [onVariantProperties] block will wire the [DisplayApksTask] input property (apkFolder) by using
the [Artifacts.get] call with the right [ArtifactType]
`apkFolder.set(artifacts.get(ArtifactType.APK))`
Since more than one APK can be produced by the build when dealing with multi-apk, you should use the
[BuiltArtifacts] interface to load the metadata associated with produced files using
[BuiltArtifacts.load] method.
`builtArtifactsLoader.get().load(apkFolder.get())'
Once loaded, the built artifacts can be accessed.
## To Run
/path/to/gradle debugDisplayApks
expected result : "Got an APK...." message.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Got an APK")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
    @Test
    fun manifestReplacementTest() {
        given {
            tasksToInvoke.add(":app:processDebugResources")
            addModule(":app") {
                buildFile =
            // language=kotlin
            """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            import com.android.build.api.artifact.ArtifactType
            ${testingElements.getGitVersionTask()}
            ${testingElements.getManifestProducerTask()}
            android {
                ${testingElements.addCommonAndroidBuildLogic()}

                val gitVersionProvider = tasks.register<GitVersionTask>("gitVersionProvider") {
                    gitVersionOutputFile.set(
                        File(project.buildDir, "intermediates/gitVersionProvider/output"))
                    outputs.upToDateWhen { false }
                }
                onVariantProperties {
                    val manifestProducer = tasks.register<ManifestProducerTask>("${'$'}{name}ManifestProducer") {
                        gitInfoFile.set(gitVersionProvider.flatMap(GitVersionTask::gitVersionOutputFile))
                        outputManifest.set(
                            File(project.buildDir, "intermediates/${'$'}{name}/ManifestProducer/output")
                        )
                    }
                    artifacts.use(manifestProducer)
                        .wiredWith(ManifestProducerTask::outputManifest)
                        .toCreate(ArtifactType.MERGED_MANIFEST)
                }
            }
                """.trimIndent()
                testingElements.addManifest(this)
                testingElements.addMainActivity(this)
            }
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            arrayOf(
                ":app:gitVersionProvider",
                ":app:debugManifestProducer"
            ).forEach {
                val task = task(it)
                assertNotNull(task)
                Truth.assertThat(task.outcome).isEqualTo(TaskOutcome.SUCCESS)
            }
            Truth.assertThat(task(":app:processDebugMainManifest")).isNull()
        }
    }

    @Test
    fun manifestTransformerTest() {
        given {
            addModule(":app") {
                buildFile =
            // language=kotlin
            """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            ${testingElements.getGitVersionTask()}

            ${testingElements.getGitVersionManifestTransformerTask()}
            android {
                ${testingElements.addCommonAndroidBuildLogic()}

                onVariantProperties {
                    val gitVersionProvider = tasks.register<GitVersionTask>("${'$'}{name}GitVersionProvider") {
                        gitVersionOutputFile.set(
                            File(project.buildDir, "intermediates/gitVersionProvider/output"))
                        outputs.upToDateWhen { false }
                    }

                    val manifestUpdater = tasks.register<ManifestTransformerTask>("${'$'}{name}ManifestUpdater") {
                        gitInfoFile.set(gitVersionProvider.flatMap(GitVersionTask::gitVersionOutputFile))
                    }
                    artifacts.use(manifestUpdater)
                        .wiredWithFiles(
                            ManifestTransformerTask::mergedManifest,
                            ManifestTransformerTask::updatedManifest)
                        .toTransform(com.android.build.api.artifact.ArtifactType.MERGED_MANIFEST)  
                }
            }
            """.trimIndent()
                testingElements.addManifest(this)
            }
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            arrayOf(
                ":app:debugGitVersionProvider",
                ":app:processDebugMainManifest",
                ":app:debugManifestUpdater"
            ).forEach {
                val task = task(it)
                assertNotNull(task)
                Truth.assertThat(task.outcome).isEqualTo(TaskOutcome.SUCCESS)
            }
        }
    }

    @Test
    fun workerEnabledTransformation() {
        val outFolderForApk = File(testProjectDir.root, "${testName.methodName}/build/acme_apks")
        given {
            tasksToInvoke.add(":app:copyDebugApks")
            addModule(":app") {
                buildFile = """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            import java.io.Serializable
            import javax.inject.Inject
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            import org.gradle.workers.WorkerExecutor
            import com.android.build.api.artifact.ArtifactType
            import com.android.build.api.artifact.ArtifactTransformationRequest
            import com.android.build.api.variant.BuiltArtifact

            import com.android.build.api.artifact.ArtifactKind
            import com.android.build.api.artifact.Artifact.SingleArtifact
            import com.android.build.api.artifact.Artifact.Replaceable
            import com.android.build.api.artifact.Artifact.ContainsMany

            sealed class AcmeArtifactType<T : FileSystemLocation>(
                kind: ArtifactKind<T>
            ) : SingleArtifact<T>(kind) {

                object ACME_APK: AcmeArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable, ContainsMany
            }

            ${testingElements.getCopyApksTask()}

            android {
                ${testingElements.addCommonAndroidBuildLogic()}

                onVariantProperties {
                    val copyApksProvider = tasks.register<CopyApksTask>("copy${'$'}{name}Apks")

                    val transformationRequest = artifacts.use(copyApksProvider)
                        .wiredWithDirectories(
                            CopyApksTask::apkFolder,
                            CopyApksTask::outFolder)
                        .toTransformMany(ArtifactType.APK)


                    copyApksProvider.configure {
                        this.transformationRequest.set(transformationRequest)
                        this.outFolder.set(File("${outFolderForApk.absolutePath}"))
                    }
                }
            }
                """.trimIndent()
                testingElements.addManifest(this)
            }
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            val task = task(":app:copydebugApks")
            assertNotNull(task)
            Truth.assertThat(task.outcome).isEqualTo(TaskOutcome.SUCCESS)
            Truth.assertThat(outFolderForApk.listFiles()?.asList()?.map { it.name }).containsExactly(
                "app-debug.apk", BuiltArtifactsImpl.METADATA_FILE_NAME
            )
        }
    }

    @Test
    fun getPublicTxt() {
        given {
            tasksToInvoke.add(":lib:validateDebugPublicResources")
            addModule(":lib") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile = // language=kotlin prefix="import org.gradle.api.*; import org.gradle.api.file.*;import org.gradle.api.provider.*; import org.gradle.api.tasks.*; import org.gradle.workers.*;"
                    """
            plugins {
                id("com.android.library")
                kotlin("android")
            }

            import com.android.build.api.artifact.ArtifactType
            import com.android.build.api.variant.BuiltArtifactsLoader
            import java.lang.RuntimeException
            import java.util.Locale
            import javax.inject.Inject

            abstract class PublicResourcesValidatorTask: DefaultTask() {

                @get:InputFile
                abstract val publicAndroidResources: RegularFileProperty

                @get:InputFile
                abstract val expectedPublicResources: RegularFileProperty

                @get:OutputDirectory
                abstract val fakeOutput: DirectoryProperty

                @get:Inject
                abstract val workerExecutor: WorkerExecutor

                @TaskAction
                fun taskAction() {
                    workerExecutor.noIsolation().submit(Action::class.java) {
                        actual.set(publicAndroidResources)
                        expected.set(expectedPublicResources)
                    }
                }

                abstract class Action: WorkAction<Action.Parameters> {
                    abstract class Parameters: WorkParameters {
                        abstract val actual: RegularFileProperty
                        abstract val expected: RegularFileProperty
                    }
                    override fun execute() {
                        val actual = parameters.actual.get().asFile.readLines()
                        val expected = parameters.expected.get().asFile.readLines()
                        if (actual != expected) {
                            throw RuntimeException(
                                    "Public Android resources have changed.\n" +
                                    "Please either revert the change or update the API expectation file\n" +
                                    "Expected\n    " + expected.joinToString("\n    ") + "\n" +
                                    "Actual\n    " + actual.joinToString("\n    ")
                                )
                        }
                        println("Public Android resources unchanged.")
                    }
                }
            }
            android {
                ${testingElements.addCommonAndroidBuildLogic()}

                onVariantProperties {
                    @OptIn(ExperimentalStdlibApi::class)
                    val capitalizedName = name.capitalize(Locale.US)
                    project.tasks.register<PublicResourcesValidatorTask>("validate${'$'}{capitalizedName}PublicResources") {
                        publicAndroidResources.set(artifacts.get(ArtifactType.PUBLIC_ANDROID_RESOURCES_LIST))
                        expectedPublicResources.set(project.file("src/test/expectedApi/public-resources.txt"))
                        fakeOutput.set(project.layout.buildDirectory.dir("intermediates/PublicResourcesValidatorTask/${'$'}name"))
                    }
                }
            }
        """.trimIndent()
                testingElements.addManifest(this)
                addSource(
                    "src/main/res/values/strings.xml",
                    """
                    <resources>
                        <string name="public_string">String</string>
                    </resources>
                    """.trimIndent())
                addSource(
                    "src/test/expectedApi/public-resources.txt",
                    "string public_string"
                )
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# Public txt get in Kotlin

This sample show how to obtain the file listing the public artifacts from the Android Gradle Plugin.
The [onVariantProperties] block will wire the [PublicResourcesValidatorTask] input property
(publicAndroidResources) by using
the [Artifacts.get] call with the right [ArtifactType].

```publicAndroidResources.set(artifacts.get(ArtifactType.PUBLIC_ANDROID_RESOURCES_LIST))```

For more information about how to mark resources as public see
[Choose resources to make public](https://developer.android.com/studio/projects/android-library.html#PrivateResources)

## To Run
/path/to/gradle validateDebugPublicResources
expected result : "Public Android resources unchanged."
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Public Android resources unchanged")
        }
    }

    @Test
    fun getMappingFile() {
        given {
            tasksToInvoke.add(":app:debugMappingFileUpload")
            addModule(":app") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.TaskAction

            import com.android.build.api.variant.BuiltArtifactsLoader
            import com.android.build.api.artifact.ArtifactType
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Internal

            abstract class MappingFileUploadTask: DefaultTask() {

                @get:InputFile
                abstract val mappingFile: RegularFileProperty

                @TaskAction
                fun taskAction() {
                    println("Uploading ${'$'}{mappingFile.get().asFile.absolutePath} to fantasy server...")
                }
            }
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
                buildTypes {
                    getByName("debug") {
                        isMinifyEnabled = true
                    }
                }

                onVariantProperties {
                    project.tasks.register<MappingFileUploadTask>("${ '$' }{name}MappingFileUpload") {
                        mappingFile.set(artifacts.get(ArtifactType.OBFUSCATION_MAPPING_FILE))
                    }
                }
            }
        """.trimIndent()
                testingElements.addManifest(this)
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# artifacts.get in Kotlin

This sample shows how to obtain the obfuscation mapping file from the AGP.
The [onVariantProperties] block will wire the [MappingFileUploadTask] input property (apkFolder) by using
the [Artifacts.get] call with the right [ArtifactType]
`mapping.set(artifacts.get(ArtifactType.OBFUSCATION_MAPPING_FILE))`
## To Run
/path/to/gradle debugMappingFileUpload
expected result : "Uploading .... to a fantasy server...s" message.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Uploading")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }

    @Test
    fun getBundleTest() {
        given {
            tasksToInvoke.add(":app:debugDisplayBundleFile")
            addModule(":app") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.TaskAction
            import com.android.build.api.variant.BuiltArtifactsLoader
            import com.android.build.api.artifact.ArtifactType
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Internal

            abstract class DisplayBundleFileTask: DefaultTask() {
                @get:InputFile
                abstract val bundleFile: RegularFileProperty

                @TaskAction
                fun taskAction() {
                    println("Got the Bundle  ${'$'}{bundleFile.get().asFile.absolutePath}")
                }
            }
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
                defaultConfig {
                    versionCode = 3
                }

                onVariantProperties {
                    project.tasks.register<DisplayBundleFileTask>("${ '$' }{name}DisplayBundleFile") {
                        bundleFile.set(artifacts.get(ArtifactType.BUNDLE))
                    }
                }
            }
        """.trimIndent()
                testingElements.addManifest(this)
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# Artifacts.get in Kotlin

This sample shows how to obtain the bundle file from the AGP.
The [onVariantProperties] block will wire the [DisplayBundleFile] input property (bundleFile) by using
the Artifacts.get call with the right ArtifactType
`bundleFile.set(artifacts.get(ArtifactType.BUNDLE))`
## To Run
/path/to/gradle debugDisplayBundleFile
expected result : "Got the Bundle ...." message.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Got the Bundle")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }

    @Test
    fun getAarTest() {
        given {
            tasksToInvoke.add(":lib:debugAarUpload")
            addModule(":lib") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.library")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.TaskAction

            import com.android.build.api.variant.BuiltArtifactsLoader
            import com.android.build.api.artifact.ArtifactType
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Internal

            abstract class AarUploadTask: DefaultTask() {

                @get:InputFile
                abstract val aar: RegularFileProperty

                @TaskAction
                fun taskAction() {
                    println("Uploading ${'$'}{aar.get().asFile.absolutePath} to fantasy server...")
                }
            }
            android {
                ${testingElements.addCommonAndroidBuildLogic()}

                onVariantProperties {
                    project.tasks.register<AarUploadTask>("${ '$' }{name}AarUpload") {
                        aar.set(artifacts.get(ArtifactType.AAR))
                    }
                }
            }
        """.trimIndent()
                testingElements.addManifest(this)
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# artifacts.get in Kotlin

This sample shows how to obtain the aar from the AGP.
The [onVariantProperties] block will wire the [AarUploadTask] input property (apkFolder) by using
the [Artifacts.get] call with the right [ArtifactType]
`aar.set(artifacts.get(ArtifactType.AAR))`
## To Run
/path/to/gradle debugAarUpload
expected result : "Uploading .... to a fantasy server...s" message.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Uploading")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
}
