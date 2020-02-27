/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.manifmerger.MergingReport
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.Files
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import java.io.File
import java.io.IOException

/** A task that processes the manifest  */
abstract class ManifestProcessorTask : IncrementalTask() {

    /** The processed Manifests files folder.  */
    @get:OutputDirectory
    abstract val manifestOutputDirectory: DirectoryProperty

    /**
     * The aapt friendly processed Manifest. In case we are processing a library manifest, some
     * placeholders may not have been resolved (and will be when the library is merged into the
     * importing application). However, such placeholders keys are not friendly to aapt which flags
     * some illegal characters. Such characters are replaced/encoded in this version.
     */
    @get:Optional
    @get:OutputDirectory
    abstract val aaptFriendlyManifestOutputDirectory: DirectoryProperty

    /** The instant app manifest which is used if we are deploying the app as an instant app.  */
    @get:Optional
    @get:OutputDirectory
    abstract val instantAppManifestOutputDirectory: DirectoryProperty

    /**
     * The aapt friendly processed Manifest. In case we are processing a library manifest, some
     * placeholders may not have been resolved (and will be when the library is merged into the
     * importing application). However, such placeholders keys are not friendly to aapt which flags
     * some illegal characters. Such characters are replaced/encoded in this version.
     */
    @get:Internal
    abstract val aaptFriendlyManifestOutputFile: File?

    /**
     * The bundle manifest which is consumed by the bundletool (as opposed to the one packaged with
     * the apk when built directly).
     */
    @get:Optional
    @get:OutputDirectory
    abstract val bundleManifestOutputDirectory: DirectoryProperty

    /**
     * The feature manifest which is consumed by its base feature (as opposed to the one packaged
     * with the feature APK). This manifest, unlike the one packaged with the APK, does not specify
     * a minSdkVersion. This is used by by both normal features and dynamic-features.
     */
    @get:Optional
    @get:OutputDirectory
    abstract val metadataFeatureManifestOutputDirectory: DirectoryProperty

    @get:Optional
    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Optional
    @get:OutputFile
    abstract val mergeBlameFile: RegularFileProperty

    companion object {
        @Throws(IOException::class)
        @JvmStatic
        protected fun outputMergeBlameContents(
            mergingReport: MergingReport, mergeBlameFile: File?
        ) {
            if (mergeBlameFile == null) {
                return
            }
            val output =
                mergingReport.getMergedDocument(MergingReport.MergedManifestKind.BLAME)
                    ?: return
            FileUtils.mkdirs(mergeBlameFile.parentFile)
            Files.newWriter(
                mergeBlameFile,
                Charsets.UTF_8
            ).use { writer -> writer.write(output) }
        }
    }
}