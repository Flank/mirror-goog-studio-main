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

package com.android.build.gradle.internal.res.shrinker.usages

import com.android.SdkConstants.DOT_DEX
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel
import com.android.builder.dexing.AnalysisCallback
import com.android.builder.dexing.runResourceShrinkerAnalysis
import java.nio.file.Files
import java.nio.file.Path

/**
 * Records resource usages, detects usages of WebViews and {@code Resources#getIdentifier},
 * gathers string constants from compiled .dex files.
 *
 * @param root directory starting from which all .dex files are analyzed.
 */
class DexClassesUsageRecorder(val root: Path) : ResourceUsageRecorder {

    override fun recordUsages(model: ResourceShrinkerModel) {
        // Record resource usages from dex classes. The following cases are covered:
        // 1. Integer constant which refers to resource id.
        // 2. Reference to static field in R classes.
        // 3. Usages of android.content.res.Resources.getIdentifier(...) and
        //    android.webkit.WebView.load...
        // 4. All strings which might be used to reference resources by name via
        //    Resources.getIdentifier.

        val classesUsageSupport = ClassesUsageSupport(model)
        Files.walk(root)
            .filter { Files.isRegularFile(it) }
            .filter { it.toString().endsWith(DOT_DEX, ignoreCase = true) }
            .forEach { recordInSingleDex(it, classesUsageSupport) }
    }

    private fun recordInSingleDex(path: Path, usageSupport: ClassesUsageSupport) {
        runResourceShrinkerAnalysis(Files.readAllBytes(path), path, object : AnalysisCallback {
            override fun shouldProcess(internalName: String): Boolean =
                !usageSupport.isResourceClass(internalName)

            override fun referencedStaticField(internalName: String, fieldName: String) =
                usageSupport.referencedStaticField(internalName, fieldName)

            override fun referencedInt(value: Int) =
                usageSupport.referencedInt("dex", value, path, path.fileName.toString())

            override fun referencedString(value: String) =
                usageSupport.referencedString(value)

            override fun referencedMethod(
                internalName: String,
                methodName: String,
                methodDescriptor: String
            ) = usageSupport.referencedMethodInvocation(
                internalName,
                methodName,
                methodDescriptor,
                internalName
            )
        })
    }
}
