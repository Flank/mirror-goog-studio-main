/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.builder.dependency

import com.android.SdkConstants
import com.android.annotations.concurrency.Immutable
import com.android.builder.model.MavenCoordinates
import com.google.common.base.Objects
import java.io.Serializable

/**
 * Serializable implementation of MavenCoordinates for use in the model.
 */
@Immutable
class MavenCoordinatesImpl @JvmOverloads constructor(
    groupId: String,
    artifactId: String,
    version: String,
    packaging: String? = null,
    classifier: String? = null
) : MavenCoordinates, Serializable {
    override val groupId: String = groupId.intern()
    override val artifactId: String = artifactId.intern()
    override val version: String = version.intern()
    override val packaging: String = packaging?.intern() ?: SdkConstants.EXT_JAR
    override val classifier: String? = classifier?.intern()

    // pre-computed derived values for performance, not part of the object identity.
    private val hashCode: Int = computeHashCode()
    private val toString: String = computeToString()
    override val versionlessId: String? = computeVersionLessId()

    fun compareWithoutVersion(coordinates: MavenCoordinates): Boolean {
        return this === coordinates ||
                Objects.equal(groupId, coordinates.groupId) &&
                Objects.equal(
                    artifactId,
                    coordinates.artifactId
                ) &&
                Objects.equal(
                    packaging,
                    coordinates.packaging
                ) &&
                Objects.equal(
                    classifier,
                    coordinates.classifier
                )
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as MavenCoordinatesImpl
        return Objects.equal(groupId, that.groupId) &&
                Objects.equal(artifactId, that.artifactId) &&
                Objects.equal(version, that.version) &&
                Objects.equal(packaging, that.packaging) &&
                Objects.equal(classifier, that.classifier)
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun toString(): String {
        return toString
    }

    private fun computeHashCode(): Int {
        return HashCodeUtils.hashCode(groupId, artifactId, version, packaging, classifier)
    }

    private fun computeToString(): String {
        val sb = StringBuilder(
            groupId.length
                    + artifactId.length
                    + version.length
                    + 2 // the 2 ':'
                    + (if (classifier != null) classifier.length + 1 else 0) // +1 for the ':'
                    + packaging.length + 1
        ) // +1 for the '@'
        sb.append(groupId).append(':').append(artifactId).append(':').append(version)
        if (classifier != null) {
            sb.append(':').append(classifier)
        }
        sb.append('@').append(packaging)
        return sb.toString().intern()
    }

    private fun computeVersionLessId(): String {
        val sb = StringBuilder(
            groupId.length
                    + artifactId.length
                    + 1 // +1 for the ':'
                    + if (classifier != null) classifier.length + 1 else 0
        ) // +1 for the ':'
        sb.append(groupId).append(':').append(artifactId)
        if (classifier != null) {
            sb.append(':').append(classifier)
        }
        return sb.toString().intern()
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}