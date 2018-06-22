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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.ide.common.build.ApkData
import com.android.utils.FileUtils
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Task to persist the {@see OutputScope#apkdatas} which allows downstream tasks to depend
 * on the {@see InternalArtifactType#APK_LIST} rather than on various complicated data structures.
 * This also allow to record the choices made during configuration time about what APKs will be
 * produced and which ones are enabled.
 */
open class MainApkListPersistence : AndroidVariantTask() {

    @get:OutputFile
    lateinit var outputFile: File
        private set

    @get:Input
    lateinit var apkData : Collection<ApkData>
        private set

    @TaskAction
    fun fullTaskAction() {

        FileUtils.deleteIfExists(outputFile)
        val apkDataList = ExistingBuildElements.persistApkList(apkData)
        FileUtils.createFile(outputFile, apkDataList)
    }

    class ConfigAction(
        val scope: VariantScope
    ) :
        TaskConfigAction<MainApkListPersistence>() {

        override val name: String
            get() = scope.getTaskName("mainApkListPersistence")
        override val type: Class<MainApkListPersistence>
            get() = MainApkListPersistence::class.java

        override fun execute(task: MainApkListPersistence) {

            task.variantName = scope.fullVariantName
            task.apkData = scope.outputScope.apkDatas

            task.outputFile = scope.artifacts.appendArtifact(
                InternalArtifactType.APK_LIST,
                task,
                SdkConstants.FN_APK_LIST)
        }
    }
}
