/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.OutputFile
import com.android.build.VariantOutput
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import java.io.Serializable
import java.util.ArrayList
import java.util.Objects
import java.util.stream.Collectors

/**
 * Information about expected Outputs from a build.
 *
 *
 * This will either contain:
 *
 *
 *  * A single main APK
 *  * A set of FULL_SPLIT (possibly some universal).
 *
 */
class OutputScope private constructor(
    val apkDatas: ImmutableList<ApkData>
) : Serializable {

    fun getSplitsByType(outputType: VariantOutput.OutputType): List<ApkData> {
        return apkDatas
            .filter { split: ApkData -> split!!.type == outputType }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        if (!super.equals(o)) {
            return false
        }
        val that = o as OutputScope
        return apkDatas == that.apkDatas
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), apkDatas)
    }

    class Builder {
        private val apkDatas: MutableList<ApkData?> =
            ArrayList()

        fun addSplit(apkData: ApkData) {
            apkDatas.add(apkData)
        }

        fun addMainSplit(apkData: ApkData) {
            if (hasMainSplits()) {
                throw RuntimeException(
                    "Cannot add "
                            + apkData
                            + " in a scope that already"
                            + " has "
                            + apkDatas.stream()
                        .filter { output: ApkData? ->
                            (output!!.type
                                    == VariantOutput.OutputType.MAIN)
                        }
                        .map { obj: ApkData? -> obj.toString() }
                        .collect(Collectors.joining(","))
                )
            }
            addSplit(apkData)
        }

        private fun hasMainSplits(): Boolean {
            return apkDatas.stream()
                .anyMatch { s: ApkData? -> s!!.type == VariantOutput.OutputType.MAIN }
        }

        fun build(): OutputScope {
            return OutputScope(
                ImmutableList.sortedCopyOf(
                    apkDatas
                )
            )
        }
    }

}