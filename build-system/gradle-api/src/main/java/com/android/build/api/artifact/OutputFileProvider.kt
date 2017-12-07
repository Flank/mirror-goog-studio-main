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

package com.android.build.api.artifact

import org.gradle.api.Incubating
import java.io.File

/** A provider of File defined in [BuildArtifactTransformBuilder].  */
@Incubating
interface OutputFileProvider {
    /**
     * Returns a File with the specified filename to be used as the output of a task.
     *
     * The File must be defined through the [BuildArtifactTransformBuilder.outputFile] before the
     * OutputProvider is created.
     *
     * @param filename the name used when the file is defined through the VariantTaskBuilder.
     * @throws RuntimeException if the file name is not already defined.
     */
    fun getFile(filename: String): File

    /**
     * Returns a File to be used as the output of a task.
     *
     * Valid only if there is a single output File defined through the
     * [BuildArtifactTransformBuilder.outputFile].
     *
     * @throws RuntimeException if no File or multiple Files was defined
     */
    val file : File
}
