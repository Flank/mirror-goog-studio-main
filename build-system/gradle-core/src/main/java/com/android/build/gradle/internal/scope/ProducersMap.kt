/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.ArtifactType
import com.android.utils.FileUtils
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Map of all the producers registered in this context.
 *
 * @param buildDirectory the project buildDirectory [DirectoryProperty]
 * @param identifier a function to uniquely indentify this context when creating files and folders.
 */
class ProducersMap<T: FileSystemLocation>(
    val objectFactory: ObjectFactory,
    val buildDirectory: DirectoryProperty,
    val identifier: ()->String) {

    private val producersMap = ConcurrentHashMap<ArtifactType, Producers<T>>()

    /**
     * Returns true if there is at least one [Producer] registered for the passed [ArtifactType]
     *
     * @param artifactType the artifact type for looked up producers.
     */
    fun hasProducers(artifactType: ArtifactType) = producersMap.containsKey(artifactType)

    /**
     * Returns a [Producers] instance (possibly empty of any [Producer]) for a passed
     * [ArtifactType]
     *
     * @param artifactType the artifact type for looked up producers.
     * @return a [Producers] instance for that [ArtifactType]
     */
    fun getProducers(artifactType: ArtifactType)=
        producersMap.getOrPut(artifactType) {
            Producers(
                artifactType,
                identifier,
                buildDirectory,
                when(artifactType.kind()) {
                    ArtifactType.Kind.DIRECTORY -> buildDirectory.dir("__EMPTY_DIR__")
                    ArtifactType.Kind.FILE -> buildDirectory.file("__EMPTY_FILE__")
                } as Provider<T>,
                objectFactory.listProperty(when(artifactType.kind()) {
                    ArtifactType.Kind.DIRECTORY -> Directory::class.java
                    ArtifactType.Kind.FILE -> RegularFile::class.java
                } as Class<T>)
            )
        }!!

    /**
     * Republishes an [ArtifactType] under a different type. This is useful when a level of
     * indirection is used.
     *
     * @param from the original [ArtifactType] for the built artifacts.
     * @param to the supplemental [ArtifactType] the same built artifacts will also be published
     * under.
     */
    fun republish(from: ArtifactType, to: ArtifactType) {
        producersMap[to] = getProducers(from)
    }

    /**
     * Copies all present and future [Producer] of [ArtifactType] from the source producers map.
     *
     * @param artifactType the artifact type for the producers to be copied in this map.
     * @param source the originating producers map to copy from.
     */
    fun copy(artifactType: ArtifactType, source: ProducersMap<out FileSystemLocation>) {
        producersMap[artifactType] = source.getProducers(artifactType) as Producers<T>
    }

    /**
     * possibly empty list of all the [org.gradle.api.Task]s (and decoration) producing this
     * artifact type.
     */
    class Producers<T : FileSystemLocation>(
        val artifactType: ArtifactType,
        val identifier: () -> String,
        val buildDirectory: DirectoryProperty,
        private val emptyProvider: Provider<T>,
        private val listProperty: ListProperty<T>) : ArrayList<Producer<T>>() {

        val buildDir:File = buildDirectory.get().asFile

        // create a unique injectable value for this artifact type. This injectable value will be
        // used for consuming the artifact. When the provider is resolved, which mean that the
        // built artifact will be used, we must resolve all file locations which will in turn
        // configure all the tasks producing this artifact type.
        val injectable: Provider<T> =
            buildDirectory.flatMap {
                // once all resolution and task configuration has happened, return the empty
                // provider if there are no producer registered.
                resolveAllAndReturnLast() ?: emptyProvider
            }

        val lastProducerTaskName: Provider<String> =
            injectable.map { _ -> get(size - 1).taskName }

        private fun resolveAll(): List<Provider<T>> {
            return synchronized(this) {
                val multipleProducers = hasMultipleProducers()
                map {
                    it.resolve(buildDir, identifier, artifactType, multipleProducers)
                }
            }
        }

        fun resolveAllAndReturnLast(): Provider<T>? = resolveAll().lastOrNull()

        fun add(settableProperty: Property<T>,
            originalProperty: Provider<Property<T>>,
            taskName: String,
            fileName: String) {
            listProperty.add(originalProperty.map { it.get() })
            add(Producer(settableProperty, originalProperty, taskName, fileName))
        }

        override fun clear() {
            super.clear()
            listProperty.empty()
        }

        fun getCurrent(): Provider<T>? {
            val currentProduct = lastOrNull() ?: return null
            return currentProduct.resolve(buildDir, identifier, artifactType, hasMultipleProducers())
        }

        fun getAllProducers(): ListProperty<T> {
            return listProperty
        }

        fun resolve(producer: Producer<T>) =
            producer.resolve(buildDir, identifier, artifactType, hasMultipleProducers())

        fun hasMultipleProducers() = size > 1
    }

    /**
     * A registered producer of an artifact. The artifact is produced by a Task identified by its
     * name and a requested file name.
     */
    class Producer<T>(
        private val settableLocation: Property<T>,
        private val originalProperty: Provider<Property<T>>,
        val taskName: String,
        val fileName: String) {
        fun resolve(buildDir: File,
            identifier: () -> String,
            artifactType: ArtifactType,
            multipleProducers: Boolean): Provider<T> {

            val fileLocation = FileUtils.join(
                artifactType.getOutputDir(buildDir),
                identifier(),
                if (multipleProducers) taskName else "",
                fileName)
            when(settableLocation) {
                is DirectoryProperty->
                    settableLocation.set(fileLocation)
                is RegularFileProperty ->
                    settableLocation.set(fileLocation)
                else -> throw RuntimeException(
                    "Property.get() is not a correct instance type : ${settableLocation.javaClass.name}")
            }
            return originalProperty.get()
        }
    }
}