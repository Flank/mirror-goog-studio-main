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

package com.android.tools.lint.client.api

import com.android.builder.model.MavenCoordinates

/**
 * Dummy implementation of [com.android.builder.model.MavenCoordinates] which
 * only stores group and artifact id's for now
 */
class DefaultMavenCoordinates(
    private val groupId: String,
    private val artifactId: String,
    private val version: String = ""
) : MavenCoordinates {

    override fun getGroupId(): String {
        return groupId
    }

    override fun getArtifactId(): String {
        return artifactId
    }

    override fun getVersion(): String {
        return version
    }

    override fun getPackaging(): String {
        return ""
    }

    override fun getClassifier(): String? {
        return ""
    }

    override fun getVersionlessId(): String {
        return "$groupId:$artifactId"
    }

    companion object {
        val NONE = DefaultMavenCoordinates("", "")
    }
}
