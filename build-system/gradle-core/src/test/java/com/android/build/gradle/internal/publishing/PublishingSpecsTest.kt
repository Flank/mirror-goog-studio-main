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

package com.android.build.gradle.internal.publishing

import com.android.build.gradle.internal.publishing.PublishingSpecs.Companion.getVariantSpec
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.builder.core.VariantTypeImpl
import com.google.common.truth.Truth.assertThat
import org.gradle.api.attributes.LibraryElements
import org.junit.Test

class PublishingSpecsTest {

    @Test
    fun allVariantTypeExist() {
        for (type in VariantTypeImpl.values()) {
            assertThat(PublishingSpecs.getVariantMap()).containsKey(type)
        }
    }

    @Test
    fun `check output spec of CLASSES_DIR artifact type`() {
        val outputSpec = getVariantSpec(VariantTypeImpl.LIBRARY).getSpec(
            AndroidArtifacts.ArtifactType.CLASSES_DIR,
            AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS
        )
        checkNotNull(outputSpec)
        assertThat(outputSpec.artifactType).isEqualTo(AndroidArtifacts.ArtifactType.CLASSES_DIR)
        assertThat(outputSpec.publishedConfigTypes).containsExactly(AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS)
        assertThat(outputSpec.outputType).isEqualTo(InternalArtifactType.RUNTIME_LIBRARY_CLASSES_DIR)
        assertThat(outputSpec.libraryElements).isEqualTo(LibraryElements.CLASSES)
    }
}
