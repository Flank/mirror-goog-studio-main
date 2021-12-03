/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.gradle.internal.scope.InternalArtifactType
import com.google.common.truth.Truth
import org.junit.Test

internal class ArtifactMetadataProcessorTest {

    /**
     * The [ArtifactMetadataProcessor.internalTypesFinalizingArtifacts] list has to be managed
     * manually instead of relying on Kotlin reflection for performance reasons. This test will use
     * Kotlin reflection to check that all [InternalArtifactType] annotated with
     * [InternalArtifactType.finalizingArtifact] are referenced in the list.
     */
    @Test
    fun testAllFinalizingArtifact() {
        val finalizingArtifactsDeclarations = mutableListOf<InternalArtifactType<*>>()
        InternalArtifactType::class.sealedSubclasses.forEach { kClass ->
            kClass.objectInstance?.let { internalArtifactType ->
                if (internalArtifactType.finalizingArtifact.isNotEmpty()) {
                    finalizingArtifactsDeclarations.add(internalArtifactType)
                }
            }
        }

        Truth.assertWithMessage(
            "The list defined in ArtifactMetadataProcessor.internalTypesFinalizingArtifacts " +
                    "is not in sync with the InternalArtifactTypes definition, check the differences" +
                    " below and update the list accordingly :")
            .that(ArtifactMetadataProcessor.internalTypesFinalizingArtifacts)
            .containsExactlyElementsIn(finalizingArtifactsDeclarations)

    }
}
