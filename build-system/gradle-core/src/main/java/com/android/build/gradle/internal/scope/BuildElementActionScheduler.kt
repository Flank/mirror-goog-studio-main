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

import com.android.ide.common.build.ApkInfo
import org.gradle.tooling.BuildException
import java.io.File

/**
 * Helper class to schedule action on {@link BuildElement}
 */
abstract class BuildElementActionScheduler {
    @Throws(BuildException::class)
    abstract fun into(type : TaskOutputHolder.TaskOutputType) : BuildElements

    @Throws(BuildException::class)
    fun into(type : TaskOutputHolder.TaskOutputType, folder: File) : BuildElements {
        return into(type).save(folder)
    }

    class Synchronous(
            val elements: BuildElements,
            val action : (apkInfo: ApkInfo, input: File) -> File?) : BuildElementActionScheduler() {

        override fun into(type: TaskOutputHolder.TaskOutputType): BuildElements {
            return BuildElements(elements
                    .asSequence()
                    .map({ input ->
                        val output = action.invoke(input.apkInfo, input.outputFile)
                        if (output == null) null
                        else BuildOutput(
                                type,
                                input.apkInfo,
                                output)
                    })
                    .filterNotNull()
                    .toList())
        }
    }
}