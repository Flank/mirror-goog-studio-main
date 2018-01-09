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

@file:JvmName("VariantUtils")
package com.android.build.gradle.integration.common.utils

import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.JavaArtifact
import com.android.builder.model.Variant

fun Variant.getAndroidTestArtifact(): AndroidArtifact {
    return getExtraAndroidArtifactByName(AndroidProject.ARTIFACT_ANDROID_TEST)
}

fun Variant.getUnitTestArtifact(): JavaArtifact {
    return getExtraJavaArtifactByName(AndroidProject.ARTIFACT_UNIT_TEST)
}

/**
 * return the only item with the given name, or throw an exception if 0 or 2+ items match
 */
fun Variant.getExtraAndroidArtifactByName(name: String): AndroidArtifact {
    return searchForExistingItem(extraAndroidArtifacts, name, AndroidArtifact::getName, "AndroidArtifact")
}

/**
 * Gets the java artifact with the given name.
 *
 * @param name the name to match, e.g. [AndroidProject.ARTIFACT_UNIT_TEST]
 * @return the only item with the given name
 * @throws AssertionError if no items match or if multiple items match
 */
fun Variant.getExtraJavaArtifactByName(name: String): JavaArtifact {
    return searchForExistingItem(extraJavaArtifacts, name, JavaArtifact::getName, "JavaArtifact")
}

/**
 * search for an item matching the name and return it if found.
 *
 */
fun Variant.getOptionalAndroidArtifact(name: String): AndroidArtifact? {
    return searchForOptionalItem(extraAndroidArtifacts, name, AndroidArtifact::getName)
}

