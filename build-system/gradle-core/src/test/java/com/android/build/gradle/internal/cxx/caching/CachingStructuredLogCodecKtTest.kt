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

package com.android.build.gradle.internal.cxx.caching

import com.android.build.gradle.internal.cxx.string.StringTable
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CachingStructuredLogCodecKtTest {
    @Test
    fun `round trip ObjectFileCacheEvent through encode and decode`() {
        val expected = ObjectFileCacheEvent.newBuilder()
            .setOutcome(ObjectFileCacheEvent.Outcome.LOADED)
            .setKeyDisplayName("key-display-name")
            .setKeyHashCode("key-hash-code")
            .setCompilation(Compilation.newBuilder()
                .setWorkingDirectory("working-directory")
                .setObjectFile("object-file")
                .setObjectFileKey(ObjectFileKey.newBuilder()
                    .addAllDependencies(listOf("dependency-1", "dependency-2"))
                    .setDependencyKey(DependenciesKey.newBuilder()
                        .setSourceFile("source-file")
                        .addAllCompilerFlags(listOf("flag-1", "flag-2"))
                    )
                )
            )
            .setHashedCompilation(Compilation.newBuilder()
                .setWorkingDirectory("hashed-working-directory")
                .setObjectFile("hashed-object-file")
                .setObjectFileKey(ObjectFileKey.newBuilder()
                    .addAllDependencies(listOf("hashed-dependency-1", "dependency-2"))
                    .setDependencyKey(DependenciesKey.newBuilder()
                        .setSourceFile("hashed-source-file")
                        .addAllCompilerFlags(listOf("hashed-flag-1", "hashed-flag-2"))
                    )
                )
            )
            .build()
        val strings = StringTable()
        val actual = expected.encode(strings).decode(strings)
        assertThat(actual).isEqualTo(expected)
    }
}
