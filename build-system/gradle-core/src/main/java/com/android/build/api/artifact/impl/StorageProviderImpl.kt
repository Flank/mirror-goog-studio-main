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

package com.android.build.api.artifact.impl

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.ArtifactKind
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import java.lang.RuntimeException

class StorageProviderImpl {

    fun lock() {
        fileStorage.disallowChanges()
        directory.disallowChanges()
    }

    companion object {
        val fileStorage = TypedStorageProvider<RegularFile>()
        val directory= TypedStorageProvider<Directory>()
}}

fun <T: FileSystemLocation> ArtifactKind<T>.storage(): TypedStorageProvider<T> {
    @Suppress("Unchecked_cast")
    return when(this) {
        ArtifactKind.FILE -> StorageProviderImpl.fileStorage
        ArtifactKind.DIRECTORY -> StorageProviderImpl.directory
        else -> throw RuntimeException("Cannot handle $this")
    } as TypedStorageProvider<T>
}

class TypedStorageProvider<T :FileSystemLocation> {
    private val singleStorage= mutableMapOf<ArtifactType.Single,  SingleArtifactContainer<T>>()
    private val multipleStorage=  mutableMapOf<ArtifactType.Multiple,  MultipleArtifactContainer<T>>()

    fun allocate(objects: ObjectFactory, artifactType: ArtifactType<T>) {
        val storage = artifactType.kind.storage()
        if (artifactType is ArtifactType.Multiple) {
            storage.multipleStorage[artifactType] =
                MultipleArtifactContainer<T> {
                    MultiplePropertyAdapter(
                        objects.listProperty(artifactType.kind.dataType().java))
                }
        } else if (artifactType is ArtifactType.Single) {
            storage.singleStorage[artifactType] =
                SingleArtifactContainer<T> {
                    SinglePropertyAdapter(
                    objects.property(artifactType.kind.dataType().java))
                }
        }
    }

    fun getArtifact(artifactType: ArtifactType.Single): ArtifactContainer<T> {

        return singleStorage[artifactType]
            ?: throw RuntimeException("Cannot find ${artifactType.name()} in single storage")
    }

    fun getArtifact(artifactType: ArtifactType.Multiple): ArtifactContainer<List<T>> {

        return multipleStorage[artifactType]
            ?: throw RuntimeException("Cannot find ${artifactType.name()} in multiple storage")
    }


    fun disallowChanges() {
        singleStorage.values.forEach { it.disallowChanges() }
        multipleStorage.values.forEach { it.disallowChanges() }
    }
}