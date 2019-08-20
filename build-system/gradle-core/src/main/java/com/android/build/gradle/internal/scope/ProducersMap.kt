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
import com.android.build.api.artifact.ArtifactKind
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Map of all the producers registered in this context.
 *
 * @param buildDirectory the project buildDirectory [DirectoryProperty]
 * @param identifier a function to uniquely identify this context when creating files and folders.
 */
class ProducersMap<T: FileSystemLocation>(
    val artifactKind: ArtifactKind<T>,
    val objectFactory: ObjectFactory,
    val buildDirectory: DirectoryProperty,
    val identifier: ()->String) {

    private val producersMap = ConcurrentHashMap<ArtifactType<T>, Producers<T>>()

    // unset properties that represent a non satisfied file or directory dependency. This is ok
    // when the dependency is Optional.
    private val emptyFileProperty = objectFactory.fileProperty().also { it.finalizeValue() }
    private val emptyDirectoryProperty = objectFactory.directoryProperty().also { it.finalizeValue() }

    /**
     * Returns true if there is at least one [Producer] registered for the passed [ArtifactType]
     *
     * @param artifactType the artifact type for looked up producers.
     */
    fun hasProducers(artifactType: ArtifactType<T>) = producersMap.containsKey(artifactType)

    /**
     * Returns a [Producers] instance (possibly empty of any [Producer]) for a passed
     * [ArtifactType]
     *
     * @param artifactType the artifact type for looked up producers.
     * @param buildLocation intended location for the artifact or not provided if using the default.
     * @return a [Producers] instance for that [ArtifactType]
     */
    internal fun getProducers(artifactType: ArtifactType<T>, buildLocation: String? = null): Producers<T> {

        val outputLocationResolver: (Producers<T>, Producer<T>) -> Provider<T> =
            if (buildLocation != null) {
                { _, producer -> producer.resolve(buildDirectory.dir(buildLocation).get().asFile) }
            } else {
                { producers, producer ->
                    val outputLocation = artifactType.getOutputDirectory(
                        buildDirectory,
                        identifier(),
                        if (producers.hasMultipleProducers()) producer.taskName else "")
                    producer.resolve(outputLocation) }
            }

        return producersMap.getOrPut(artifactType) {
            Producers(
                artifactType,
                identifier,
                outputLocationResolver,
                when (artifactType.kind) {
                    ArtifactType.DIRECTORY -> emptyDirectoryProperty
                    ArtifactType.FILE -> emptyFileProperty
                    else -> throw java.lang.RuntimeException("${artifactType.kind} is not handled.")
                } as Provider<T>,
                objectFactory.listProperty(
                    when (artifactType.kind) {
                        ArtifactType.DIRECTORY -> Directory::class.java
                        ArtifactType.FILE -> RegularFile::class.java
                        else -> throw java.lang.RuntimeException("${artifactType.kind} is not handled.")
                    } as Class<T>
                ),
                objectFactory.listProperty(Provider::class.java as Class<Provider<T>>)
            )
        }!!
    }

    /**
     * Republishes an [ArtifactType] under a different type. This is useful when a level of
     * indirection is used.
     *
     * @param from the original [ArtifactType] for the built artifacts.
     * @param to the supplemental [ArtifactType] the same built artifacts will also be published
     * under.
     */
    fun republish(from: ArtifactType<T>, to: ArtifactType<T>) {
        producersMap[to] = getProducers(from)
    }

    /**
     * Copies all present and future [Producer] of [ArtifactType] from the source producers map.
     *
     * @param artifactType the artifact type for the producers to be copied in this map.
     * @param source the originating producers map to copy from.
     */
    fun copy(artifactType: ArtifactType<T>, source: Producers<out FileSystemLocation>) {
        producersMap[artifactType] = source as Producers<T>
    }

    fun entrySet() = producersMap.entries

    /**
     * possibly empty list of all the [org.gradle.api.Task]s (and decoration) producing this
     * artifact type.
     */
    class Producers<T : FileSystemLocation>(
        val artifactType: ArtifactType<T>,
        val identifier: () -> String,
        val resolver: (Producers<T>, Producer<T>) -> Provider<T>,
        private val emptyProvider: Provider<T>,
        private val listProperty: ListProperty<T>,
        val dependencies: ListProperty<Provider<T>>) : ArrayList<Producer<T>>() {

        // create a unique injectable value for this artifact type. This injectable value will be
        // used for consuming the artifact. When the provider is resolved, which mean that the
        // built artifact will be used, we must resolve all file locations which will in turn
        // configure all the tasks producing this artifact type.
        val injectable: Provider<T> =
            dependencies.flatMap {
                // once all resolution and task configuration has happened, return the empty
                // provider if there are no producer registered.
                resolveAllAndReturnLast() ?: emptyProvider
            }

        val lastProducerTaskName: Provider<String> =
            injectable.map { _ -> get(size - 1).taskName }

        // keep count of all producers. Even if a producer is replaced, we still need to remember
        // its existence so we do not have overlapping output with different task.
        // For instance Task1 outputs in O1, and Task2 comes around and want to replace the artifact
        // with output at O2, we must make sure that O1 and O2 do not overlap.
        private val producerCount = AtomicInteger(0)

        private fun resolveAll(): List<Provider<T>> {
            return synchronized(this) {
                map {
                    resolver(this, it)
                }
            }
        }

        fun resolveAllAndReturnLast(): Provider<T>? = resolveAll().lastOrNull()

        fun add(
            settableProperty: Property<T>,
            originalProperty: Provider<Property<T>>,
            taskName: String,
            fileName: String
        ) {
            producerCount.incrementAndGet()
            listProperty.add(originalProperty.map { it.get() })
            dependencies.add(originalProperty)
            add(PropertyBasedProducer(settableProperty, originalProperty, taskName, fileName))
        }

        fun add(
            provider: Provider<Provider<T>>,
            taskName: String) = add(ProviderBasedProducer(provider, taskName))


        override fun clear() {
            super.clear()
            listProperty.empty()
        }

        fun getCurrent(): Provider<T>? {
            val currentProduct = lastOrNull() ?: return null
            return resolver(this, currentProduct)
        }

        fun getAllProducers(): ListProperty<T> {
            return listProperty
        }

        fun resolve(producer: Producer<T>) =
            resolver(this, producer)

        // even if we currently have only one, but more than one was registered, return true so
        // we do not have overlapping tasks output.
        fun hasMultipleProducers() = producerCount.get() > 1

        fun toProducersData() = BuildArtifactsHolder.ProducersData(
            map { producer -> producer.toProducerData() }
        )
    }

    /**
     * Interface for all producer.
     */
    interface Producer<T: FileSystemLocation> {
        /**
         * resolve this producer in the context of the passed [outputDirectory]
         */
        fun resolve(outputDirectory: File): Provider<T>

        /**
         * build [BuildArtifactsHolder.ProducerData] from this producer.
         */
        fun toProducerData(): BuildArtifactsHolder.ProducerData {
            val originalProperty = asProvider()
            return if (originalProperty.isPresent && originalProperty.get().isPresent) {
                BuildArtifactsHolder.ProducerData(listOf(originalProperty.get().get().asFile.path), taskName)
            } else {
                BuildArtifactsHolder.ProducerData(listOf(), taskName)
            }
        }

        /**
         * Return the producer as a Provider<out Provider<T>>
         */
        fun asProvider(): Provider<out Provider<T>>

        /**
         * task name producing the artifact.
         */
        val taskName: String
    }

    /**
     * A [Producer] that's based on a [Provider] which mean that its location cannot be reset or
     * changed.
     */
    private class ProviderBasedProducer<T: FileSystemLocation>(
        private val provider: Provider<Provider<T>>,
        override val taskName: String): Producer<T> {

        override fun resolve(outputDirectory: File): Provider<T> {
            return provider.get()
        }

        override fun asProvider(): Provider<out Provider<T>> {
            return provider
        }
    }

    /**
     * A [Producer] that's based on a [Property] which mean that its location can be changed
     * depending on factors like if there are more than one providers at execution time.
     * The artifact is produced by a Task identified by its name and a requested file name.
     */
    private class PropertyBasedProducer<T: FileSystemLocation>(
        private val settableLocation: Property<T>,
        private val originalProperty: Provider<Property<T>>,
        override val taskName: String,
        private val fileName: String): Producer<T> {

        override fun resolve(outputDirectory: File): Provider<T> {

            val fileLocation = File(outputDirectory, fileName)
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

        override fun asProvider(): Provider<out Provider<T>> {
            return originalProperty
        }
    }
}