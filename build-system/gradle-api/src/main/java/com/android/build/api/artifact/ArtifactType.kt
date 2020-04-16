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

package com.android.build.api.artifact

import org.gradle.api.Incubating
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import java.util.Locale
import java.io.Serializable

/**
 * Defines a type of artifact handled by the Android Gradle Plugin.
 *
 * Each instance of [ArtifactType] is produced by a [org.gradle.api.Task] and potentially consumed by
 * one to many tasks.
 *
 * An artifact can potentially be produced by more than one tasks (each task acting in an additive
 * behavior), but consumers must be aware when more than one artifacts can be present,
 * implementing [Multiple] interface will indicate such requirement.
 *
 * An artifact must be one the supported [ArtifactKind] which must be provided at construction time
 * ArtifactKind also defines the concrete [FileSystemLocation] subclass used.
 */
@Incubating
abstract class ArtifactType<T: FileSystemLocation>(val kind: ArtifactKind<T>): Serializable {

    /**
     * Provide a unique name for the artifact type. For external plugins defining new types,
     * consider adding the plugin name to the artifact's name to avoid collision with other plugins.
     */
    fun name(): String = javaClass.simpleName

    /**
     * @return the folder name under which the artifact files or folders should be stored.
     */
    open fun getFolderName(): String = name().toLowerCase(Locale.US)

    /**
     * @return Depending on [T] the file name of folder under the variant specific folder, empty
     * string to use defaults.
     */
    open fun getFileSystemLocationName(): String = ""

    /**
     * Supported [ArtifactKind]
     */
    @Incubating
    companion object {
        /**
         * [ArtifactKind] for [RegularFile]
         */
        @JvmField
        val FILE = ArtifactKind.FILE

        /**
         * [ArtifactKind] for [Directory]
         */
        @JvmField
        val DIRECTORY = ArtifactKind.DIRECTORY
    }

    /**
     * Denotes possible multiple [FileSystemLocation] instances for this artifact type.
     * Consumers of artifact types that are multiple must be consuming collection of
     * [FileSystemLocation]
     */
    @Incubating
    interface Multiple  {
        fun name(): String
    }

    /**
     * Denotes a single [FileSystemLocation] instance of this artifact type at a given time.
     * Single artifact types can be transformed or replaced but never appended.
     * Consumers of artifact types that are multiple must be consuming collection of
     * [FileSystemLocation]
     */
    @Incubating
    interface Single {
        fun name(): String
    }


    /**
     * Denotes a single [DIRECTORY] that may contain zero to many
     * [com.android.build.api.variant.BuiltArtifact].
     *
     * Artifact types annotated with this marker interface are backed up by a [DIRECTORY] which
     * content should be read using the [com.android.build.api.variant.BuiltArtifactsLoader].
     *
     * If producing an artifact type annotated with this marker interface, content should be
     * written using the [com.android.build.api.variant.BuiltArtifacts.save] methods.
     */
    @Incubating
    interface ContainsMany

    /**
     * Denotes an artifact type that can be appended to.
     * Appending means that existing artifacts produced by other tasks are untouched and a
     * new task producing the artifact type will have its output appended to the list of artifacts.
     *
     * Due to the additive behavior of the append scenario, an [Appendable] is by definition also
     * [Multiple]
     */
    @Incubating
    interface Appendable: Multiple

    /**
     * Denotes an artifact type that can transformed.
     *
     * Either a [Single] or [Multiple] artifact type can be transformed.
     */
    @Incubating
    interface Transformable

    /**
     * Denotes an artifact type that can be replaced.
     * Only [Single] artifacts can be replaced, if you want to replace a [Multiple] artifact type,
     * you will need to transform it by combining all the inputs into a single output instance.
     */
    @Incubating
    interface Replaceable: Single
}


