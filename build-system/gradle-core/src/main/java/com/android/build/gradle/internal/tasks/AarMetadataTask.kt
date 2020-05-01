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
package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.xml.XmlBuilder
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import java.io.File
import javax.inject.Inject

/** A task that writes the AAR metadata file  */
@CacheableTask
abstract class AarMetadataTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:Input
    abstract val aarVersion: Property<String>

    @get:Input
    abstract val aarMetadataVersion: Property<String>

    override fun doTaskAction() {
        AarMetadataTaskDelegate(
            workerExecutor.noIsolation(),
            output.get().asFile,
            aarVersion.get(),
            aarMetadataVersion.get()
        ).run()
    }

    class CreationAction(
        creationConfig: LibraryCreationConfig
    ) : VariantTaskCreationAction<AarMetadataTask, LibraryCreationConfig>(creationConfig) {

        override val name: String
            get() = computeTaskName("create", "AarMetadata")

        override val type: Class<AarMetadataTask>
            get() = AarMetadataTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<out AarMetadataTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.operations
                .setInitialProvider(taskProvider, AarMetadataTask::output)
                .withName(aarMetadataFileName)
                .on(InternalArtifactType.AAR_METADATA)
        }

        override fun configure(task: AarMetadataTask) {
            super.configure(task)

            task.aarVersion.setDisallowChanges(aarVersion)
            task.aarMetadataVersion.setDisallowChanges(aarMetadataVersion)
        }
    }

    companion object {
        const val aarMetadataFileName = "AarMetadata.xml"
        const val aarMetadataEntryPath = "META-INF/com/android/build/gradle/$aarMetadataFileName"
        const val aarVersion = "1.0"
        const val aarMetadataVersion = "1.0"
    }
}

/** Delegate to write the AAR metadata file */
class AarMetadataTaskDelegate(
    private val workQueue: WorkQueue,
    val output: File,
    private val aarVersion: String,
    private val aarMetadataVersion: String
) {

    fun run() {
        workQueue.submit(AarMetadataWorkAction::class.java) {
            it.output.set(output)
            it.aarVersion.set(aarVersion)
            it.aarMetadataVersion.set(aarMetadataVersion)
        }
    }
}

/** [WorkAction] to write AAR metadata file */
abstract class AarMetadataWorkAction @Inject constructor(
    private val aarMetadataWorkParameters: AarMetadataWorkParameters
): WorkAction<AarMetadataWorkParameters> {

    override fun execute() {
        val xmlBuilder = XmlBuilder()
        xmlBuilder.startTag("aar-metadata")
        xmlBuilder.attribute("aarVersion", aarMetadataWorkParameters.aarVersion.get())
        xmlBuilder.attribute(
            "aarMetadataVersion",
            aarMetadataWorkParameters.aarMetadataVersion.get()
        )
        xmlBuilder.endTag("aar-metadata")
        aarMetadataWorkParameters.output.get().asFile.writeText(xmlBuilder.toString())
    }
}

/** [WorkParameters] for [AarMetadataWorkAction] */
abstract class AarMetadataWorkParameters: WorkParameters {
    abstract val output: RegularFileProperty
    abstract val aarVersion: Property<String>
    abstract val aarMetadataVersion: Property<String>
}
