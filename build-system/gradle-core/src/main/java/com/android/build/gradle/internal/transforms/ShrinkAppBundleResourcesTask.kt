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

package com.android.build.gradle.internal.transforms

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.res.shrinker.ApkFormat
import com.android.build.gradle.internal.res.shrinker.LoggerAndFileDebugReporter
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerImpl
import com.android.build.gradle.internal.res.shrinker.gatherer.ProtoResourceTableGatherer
import com.android.build.gradle.internal.res.shrinker.graph.ProtoResourcesGraphBuilder
import com.android.build.gradle.internal.res.shrinker.obfuscation.ProguardMappingsRecorder
import com.android.build.gradle.internal.res.shrinker.usages.DexUsageRecorder
import com.android.build.gradle.internal.res.shrinker.usages.ProtoAndroidManifestUsageRecorder
import com.android.build.gradle.internal.res.shrinker.usages.ToolsAttributeUsageRecorder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.utils.FileUtils
import java.nio.file.Files
import javax.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

/**
 * Task to shrink resources inside Android app bundle. Consumes a bundle built by bundletool
 * and replaces unused resources with dummy content there and outputs bundle with replaced
 * resources.
 *
 * Enabled when android.experimental.enableNewResourceShrinker=true.
 */
abstract class ShrinkAppBundleResourcesTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val shrunkBundle: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val originalBundle: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rawResourceDir: DirectoryProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(ShrinkAppBundleResourcesAction::class.java) {
            it.originalBundle.set(originalBundle)
            it.shrunkBundle.set(shrunkBundle)
            it.rawResourcesDir.set(rawResourceDir)
            it.report.set(shrunkBundle.get().asFile.resolveSibling("resources.txt"))
        }
    }

    class CreationAction(componentProperties: ComponentPropertiesImpl) :
        VariantTaskCreationAction<ShrinkAppBundleResourcesTask, ComponentPropertiesImpl>(
            componentProperties
        ) {

        override val name: String = computeTaskName("shrinkBundle", "Resources")
        override val type: Class<ShrinkAppBundleResourcesTask>
            get() = ShrinkAppBundleResourcesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<ShrinkAppBundleResourcesTask>) {
            creationConfig.artifacts.use(taskProvider).toTransform(
                InternalArtifactType.INTERMEDIARY_BUNDLE,
                ShrinkAppBundleResourcesTask::originalBundle,
                ShrinkAppBundleResourcesTask::shrunkBundle
            )
        }

        override fun configure(task: ShrinkAppBundleResourcesTask) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_NOT_COMPILED_RES,
                task.rawResourceDir
            )
        }
    }
}

interface ResourceShrinkerParams : WorkParameters {
    val originalBundle: RegularFileProperty
    val shrunkBundle: RegularFileProperty
    val rawResourcesDir: DirectoryProperty
    val report: RegularFileProperty
}

private abstract class ShrinkAppBundleResourcesAction @Inject constructor() :
    WorkAction<ResourceShrinkerParams> {

    override fun execute() {
        val logger = Logging.getLogger(LegacyShrinkBundleModuleResourcesTask::class.java)
        FileUtils.createZipFilesystem(originalBundleFile.toPath()).use { fs ->
            val proguardMappings = fs.getPath(
                "BUNDLE-METADATA",
                "com.android.tools.build.obfuscation",
                "proguard.map"
            )

            ResourceShrinkerImpl(
                ProtoResourceTableGatherer(fs.getPath(BASE_MODULE, "resources.pb")),
                proguardMappings
                    .takeIf { Files.isRegularFile(it) }
                    ?.let { ProguardMappingsRecorder(it) },
                listOf(
                    DexUsageRecorder(fs.getPath(BASE_MODULE, "dex")),
                    ProtoAndroidManifestUsageRecorder(
                        fs.getPath(BASE_MODULE, "manifest", "AndroidManifest.xml")
                    ),
                    ToolsAttributeUsageRecorder(rawResourcesDirFile.toPath())
                ),
                ProtoResourcesGraphBuilder(
                    fs.getPath(BASE_MODULE, "res"),
                    fs.getPath(BASE_MODULE, "resources.pb")
                ),
                LoggerAndFileDebugReporter(logger, reportFile),
                ApkFormat.PROTO
            ).use { shrinker ->
                shrinker.analyze()

                shrinker.rewriteResourceZip(originalBundleFile, shrunkBundleFile, BASE_MODULE)

                // Dump some stats
                if (shrinker.unusedResourceCount > 0) {
                    val before = originalBundleFile.length()
                    val after = shrunkBundleFile.length()
                    val percent = ((before - after) * 100 / before).toInt()

                    val stat = "Removed unused resources: Binary bundle size reduced from " +
                            "${toKbString(before)}KB to ${toKbString(after)}KB. Removed $percent%"
                    logger.info(stat)
                }
            }
        }
    }

    private val originalBundleFile by lazy { parameters.originalBundle.get().asFile }
    private val shrunkBundleFile by lazy { parameters.shrunkBundle.get().asFile }
    private val rawResourcesDirFile by lazy { parameters.rawResourcesDir.get().asFile }
    private val reportFile by lazy { parameters.report.get().asFile }

    companion object {
        private val BASE_MODULE = "base"

        private fun toKbString(size: Long): String {
            return (size.toInt() / 1024).toString()
        }
    }
}
