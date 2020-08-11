/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.builder.dexing

import java.io.File
import java.io.Serializable

/**
 * A bucket of [DexArchiveEntry]'s. Multiple buckets are split from the collection of dex archive
 * entries inside the given dex roots, which can be directories or jars. Each bucket is identified
 * by a bucket number.
 */
class DexArchiveEntryBucket(
    private val dexRoots: List<File>,
    val numberOfBuckets: Int,
    private val bucketNumber: Int
) : Serializable {

    /**
     * Returns the [DexArchiveEntry]'s in this bucket.
     *
     * @param computeBucketNumber Function that returns the bucket number for a dex file or jar
     *     entry having the given relative path. It is used to split the dex archive entries inside
     *     the dex roots into buckets.
     */
    fun getDexArchiveEntries(
        computeBucketNumber: (relativePath: String, numberOfBuckets: Int) -> Int
    ): List<DexArchiveEntry> {
        return dexRoots.flatMap { dexRoot ->
            DexArchives.fromInput(dexRoot.toPath()).use { dexArchive ->
                dexArchive.getSortedDexArchiveEntries { relativePath: String ->
                    computeBucketNumber(relativePath, numberOfBuckets) == bucketNumber
                }
            }
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
