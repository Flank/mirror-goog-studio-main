/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.builder.model.v2

import java.io.File

/**
 * An Android Artifact.
 *
 * This is the entry point for the output of a [Variant]. This can be more than one
 * output in the case of multi-apk where more than one APKs are generated from the same set
 * of sources.
 *
 */
interface AndroidArtifact : BaseArtifact {
    /**
     * Returns whether the output file is signed. This can only be true for the main apk of an
     * application project.
     *
     * @return true if the app is signed.
     */
    val isSigned: Boolean

    /**
     * Returns the name of the [SigningConfig] used for the signing. If none are setup or if
     * this is not the main artifact of an application project, then this is null.
     *
     * @return the name of the setup signing config.
     */
    val signingConfigName: String?

    /**
     * Returns the application id of this artifact.
     *
     * @return the application id.
     */
    val applicationId: String

    /**
     * Returns the name of the task used to generate the source code. The actual value might
     * depend on the build system front end.
     *
     * @return the name of the code generating task.
     */
    val sourceGenTaskName: String

    /**
     * Returns all the resource folders that are generated. This is typically the renderscript
     * output and the merged resources.
     *
     * @return a list of folder.
     */
    val generatedResourceFolders: Collection<File>

    /**
     * Returns the ABI filters associated with the artifact, or null if there are no filters.
     *
     * If the list contains values, then the artifact only contains these ABIs and excludes
     * others.
     */
    val abiFilters: Set<String>?

    /**
     * Returns a list of additional APKs that need to installed on the device for this artifact to
     * work correctly.
     *
     *
     * For test artifacts, these will be "buddy APKs" from the `androidTestUtil`
     * configuration.
     *
     * @since 3.0
     */
    val additionalRuntimeApks: Collection<File>

    /**
     * Returns the test options only if the variant type is testing.
     *
     * @since 3.0
     */
    val testOptions: TestOptions?

    /**
     * Returns the name of the task used to run instrumented tests or null if the variant is not a
     * test variant.
     *
     * @since 3.1
     * @return name of the task used to run instrumented tests
     */
    val instrumentedTestTaskName: String?

    /**
     * Returns the name of the task used to generate the bundle file (.aab), or null if the task is
     * not supported.
     *
     * @since 3.2
     * @return name of the task used to generate the bundle file (.aab)
     */
    val bundleTaskName: String?

    /**
     * Returns the path to the listing file generated after each [.getBundleTaskName] task
     * execution. The listing file will contain a reference to the produced bundle file (.aab).
     * Returns null when [.getBundleTaskName] returns null.
     *
     * @since 4.0
     * @return the file path for the bundle model file.
     */
    val bundleTaskOutputListingFile: String?

    /**
     * Returns the name of the task used to generate APKs via the bundle file (.aab), or null if the
     * task is not supported.
     *
     * @since 3.2
     * @return name of the task used to generate the APKs via the bundle
     */
    val apkFromBundleTaskName: String?

    /**
     * Returns the path to the model file generated after each [.getApkFromBundleTaskName]
     * task execution. The model will contain a reference to the folder where APKs from bundle are
     * placed into. Returns null when [.getApkFromBundleTaskName] returns null.
     *
     * @since 4.0
     * @return the file path for the [.getApkFromBundleTaskName] output model.
     */
    val apkFromBundleTaskOutputListingFile: String?

    /**
     * Returns the code shrinker used by this artifact or null if no shrinker is used to build this
     * artifact.
     */
    val codeShrinker: CodeShrinker?
}