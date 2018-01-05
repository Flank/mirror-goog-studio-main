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

package com.android.build.gradle.tasks

import android.databinding.tool.DataBindingBuilder
import com.android.SdkConstants

import com.android.build.gradle.AndroidConfig
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.LibraryVariantData
import com.android.build.gradle.internal.variant.TaskContainer
import com.android.builder.core.BuilderConstants
import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.bundling.Zip
import java.io.File
import java.util.concurrent.Callable

/** Custom Zip task to allow archive name to be set lazily. */
open class AndroidZip : Zip() {
    private lateinit var archiveNameSupplier: () -> String

    @Input
    override fun getArchiveName() = archiveNameSupplier()

    class ConfigAction(
            private val extension: AndroidConfig,
            private val variantScope: VariantScope
    ) : TaskConfigAction<AndroidZip> {

        override fun getName() = variantScope.getTaskName("bundle")

        override fun getType() = AndroidZip::class.java

        override fun execute(bundle: AndroidZip) {
            val libVariantData = variantScope.variantData as LibraryVariantData

            libVariantData.addTask(TaskContainer.TaskKind.PACKAGE_ANDROID_ARTIFACT, bundle)

            // Sanity check, there should never be duplicates.
            bundle.duplicatesStrategy = DuplicatesStrategy.FAIL
            // Make the AAR reproducible. Note that we package several zips inside the AAR, so all of
            // those need to be reproducible too before we can switch this on.
            // https://issuetracker.google.com/67597902
            bundle.isReproducibleFileOrder = true
            bundle.isPreserveFileTimestamps = false

            bundle.description = ("Assembles a bundle containing the library in "
                    + variantScope.variantConfiguration.fullName
                    + ".")

            bundle.destinationDir = variantScope.aarLocation
            bundle.archiveNameSupplier = { variantScope.outputScope.mainSplit.outputFileName }
            bundle.extension = BuilderConstants.EXT_LIB_ARCHIVE
            bundle.from(
                    variantScope.getOutput(TaskOutputType.AIDL_PARCELABLE),
                    prependToCopyPath(SdkConstants.FD_AIDL))
            bundle.from(variantScope.getOutput(TaskOutputType.CONSUMER_PROGUARD_FILE))
            if (extension.dataBinding.isEnabled) {
                bundle.from(
                        variantScope.getOutput(TaskOutputType.DATA_BINDING_ARTIFACT),
                        prependToCopyPath(DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR))
                bundle.from(
                        variantScope.getOutput(TaskOutputType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT),
                        prependToCopyPath(
                                DataBindingBuilder.DATA_BINDING_CLASS_LOG_ROOT_FOLDER_IN_AAR))
            }
            bundle.from(variantScope.getOutput(TaskOutputType.LIBRARY_MANIFEST))
            // TODO: this should be unconditional b/69358522
            if (java.lang.Boolean.TRUE != variantScope.globalScope.extension.aaptOptions.namespaced) {
                bundle.from(variantScope.getOutput(TaskOutputType.SYMBOL_LIST))
                bundle.from(
                        variantScope.getOutput(TaskOutputType.PACKAGED_RES),
                        prependToCopyPath(SdkConstants.FD_RES))
            }
            bundle.from(
                    variantScope.getOutput(TaskOutputType.RENDERSCRIPT_HEADERS),
                    prependToCopyPath(SdkConstants.FD_RENDERSCRIPT))
            bundle.from(variantScope.getOutput(TaskOutputType.PUBLIC_RES))
            if (variantScope.hasOutput(TaskOutputType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)) {
                bundle.from(variantScope.getOutput(TaskOutputType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR))
            }
            if (variantScope.hasOutput(TaskOutputType.RES_STATIC_LIBRARY)) {
                bundle.from(variantScope.getOutput(TaskOutputType.RES_STATIC_LIBRARY))
            }
            bundle.from(
                    variantScope.getOutput(TaskOutputType.LIBRARY_AND_LOCAL_JARS_JNI),
                    prependToCopyPath(SdkConstants.FD_JNI))
            bundle.from(variantScope.globalScope.getOutput(TaskOutputType.LINT_JAR))
            if (variantScope.hasOutput(TaskOutputType.ANNOTATIONS_ZIP)) {
                bundle.from(variantScope.getOutput(TaskOutputType.ANNOTATIONS_ZIP))
            }
            bundle.from(variantScope.getOutput(TaskOutputType.AAR_MAIN_JAR))
            bundle.from(
                    variantScope.getOutput(TaskOutputType.AAR_LIBS_DIRECTORY),
                    prependToCopyPath(SdkConstants.LIBS_FOLDER))
            bundle.from(
                    variantScope.getOutput(TaskOutputType.LIBRARY_ASSETS),
                    prependToCopyPath(SdkConstants.FD_ASSETS))

            variantScope.addTaskOutput(
                    TaskOutputType.AAR,
                    Callable {
                        File(
                                variantScope.aarLocation,
                                variantScope
                                        .outputScope
                                        .mainSplit
                                        .outputFileName)
                    },
                    bundle.name)

            libVariantData.packageLibTask = bundle
        }

        private fun prependToCopyPath(pathSegment: String) = Action {
            copySpec: CopySpec ->
                copySpec.eachFile(
                        { fileCopyDetails: FileCopyDetails ->
                            fileCopyDetails.relativePath =
                                    fileCopyDetails.relativePath.prepend(pathSegment)
                        })
        }
    }
}
