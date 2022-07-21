/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.wizard.template.impl.activities.cppGameActivity

import com.android.tools.idea.wizard.template.DEFAULT_CMAKE_VERSION
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.deriveNativeLibraryName
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.androidOutCpp
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.androidOutH
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.cppGameActivityJava
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.cppGameActivityKt
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.modelH
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.nativeLibCpp
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.rendererCpp
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.rendererH
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.shaderCpp
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.shaderH
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.textureAssetCpp
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.textureAssetH
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.utilityCpp
import com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src.utilityH
import java.io.File

fun RecipeExecutor.generateCppGameActivity(
    moduleData: ModuleTemplateData,
    activityClass: String,
    isLauncher: Boolean,
    packageName: PackageName,
    cppFlags: String
) {
    val (projectData, srcOut) = moduleData
    val ktOrJavaExt = projectData.language.extension

    addDependency("com.google.android.material:material:1.4.+", minRev = "1.4.0")

    // things needed for game-activity
    setBuildFeature("prefab", true)
    addDependency("androidx.games:games-activity:1.0.+", minRev = "1.0.0")

    setCppOptions(
        cppFlags = cppFlags,
        cppPath = "src/main/cpp/CMakeLists.txt",
        cppVersion = DEFAULT_CMAKE_VERSION
    )

    val libraryName = packageName.deriveNativeLibraryName()

    generateManifest(
        moduleData , activityClass, packageName, isLauncher, false,
        generateActivityTitle = false, libraryName = libraryName
    )

    addAllKotlinDependencies(moduleData)

    val simpleActivityPath = srcOut.resolve("$activityClass.$ktOrJavaExt")

    val simpleActivity = when (projectData.language) {
        Language.Kotlin -> cppGameActivityKt(
            packageName = packageName,
            activityClass = activityClass,
            libraryName = libraryName,
        )
        Language.Java -> cppGameActivityJava(
            packageName = packageName,
            activityClass = activityClass,
            libraryName = libraryName,
        )
    }
    save(simpleActivity, simpleActivityPath)

    val nativeSrcOut = moduleData.rootDir.resolve("src/main/cpp")
    val nativeLibCpp = "main.cpp"
    save(nativeLibCpp(), nativeSrcOut.resolve(nativeLibCpp))
    save(androidOutCpp(), nativeSrcOut.resolve("AndroidOut.cpp"))
    save(androidOutH(), nativeSrcOut.resolve("AndroidOut.h"))
    save(modelH(), nativeSrcOut.resolve("Model.h"))
    save(rendererCpp(), nativeSrcOut.resolve("Renderer.cpp"))
    save(rendererH(), nativeSrcOut.resolve("Renderer.h"))
    save(shaderCpp(), nativeSrcOut.resolve("Shader.cpp"))
    save(shaderH(), nativeSrcOut.resolve("Shader.h"))
    save(textureAssetCpp(), nativeSrcOut.resolve("TextureAsset.cpp"))
    save(textureAssetH(), nativeSrcOut.resolve("TextureAsset.h"))
    save(utilityCpp(), nativeSrcOut.resolve("Utility.cpp"))
    save(utilityH(), nativeSrcOut.resolve("Utility.h"))
    save(
        gameActivityCMakeListsTxt(nativeLibCpp, libraryName),
        nativeSrcOut.resolve("CMakeLists.txt")
    )

    copy(
        File("cpp-game-activity").resolve("assets"),
        moduleData.resDir.resolve("../assets")
    )

    open(simpleActivityPath)
}
