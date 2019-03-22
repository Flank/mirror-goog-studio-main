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
package com.android.ide.common.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KnownVersionStabilityTest {

    @Test
    fun testStabilityOf() {
        assertThat(stabilityOf("com.android.support", "appcompat-v7"))
            .isEqualTo(KnownVersionStability.INCOMPATIBLE)
        assertThat(stabilityOf("androidx.appcompat", "appcompat"))
            .isEqualTo(KnownVersionStability.SEMANTIC)

        assertThat(stabilityOf("com.android.support", "design"))
            .isEqualTo(KnownVersionStability.INCOMPATIBLE)
        assertThat(stabilityOf("com.google.android.material", "material"))
            .isEqualTo(KnownVersionStability.SEMANTIC)

        assertThat(stabilityOf("com.android.support.constraint", "constraint-layout"))
            .isEqualTo(KnownVersionStability.INCOMPATIBLE)
        assertThat(stabilityOf("androidx.constraintlayout", "constraintlayout"))
            .isEqualTo(KnownVersionStability.SEMANTIC)

        assertThat(stabilityOf("com.google.firebase", "firebase-core", "14.3.1"))
            .isEqualTo(KnownVersionStability.INCOMPATIBLE)
        assertThat(stabilityOf("com.google.firebase", "firebase-core", "15.0.1"))
            .isEqualTo(KnownVersionStability.SEMANTIC)

        assertThat(stabilityOf("com.google.android.gms", "play-services-ads", "14.3.1"))
            .isEqualTo(KnownVersionStability.INCOMPATIBLE)
        assertThat(stabilityOf("com.google.android.gms", "play-services-ads", "15.0.1"))
            .isEqualTo(KnownVersionStability.SEMANTIC)

        assertThat(stabilityOf("org.jetbrains.kotlin", "kotlin-stdlib"))
            .isEqualTo(KnownVersionStability.STABLE)
        assertThat(stabilityOf("org.jetbrains.kotlin", "kotlin-reflect"))
            .isEqualTo(KnownVersionStability.INCREMENTAL)
    }

    @Test
    fun testExpiration() {
        assertThat(KnownVersionStability.INCOMPATIBLE.expiration(GradleVersion(3, 4, 5)))
            .isEqualTo(GradleVersion(3, 4, 6))
        assertThat(KnownVersionStability.INCREMENTAL.expiration(GradleVersion(3, 4, 5)))
            .isEqualTo(GradleVersion(3, 5, 0))
        assertThat(KnownVersionStability.SEMANTIC.expiration(GradleVersion(3, 4, 5)))
            .isEqualTo(GradleVersion(4, 0, 0))
        assertThat(KnownVersionStability.STABLE.expiration(GradleVersion(3, 4, 5)))
            .isEqualTo(GradleVersion(Int.MAX_VALUE, 0, 0))
    }
}