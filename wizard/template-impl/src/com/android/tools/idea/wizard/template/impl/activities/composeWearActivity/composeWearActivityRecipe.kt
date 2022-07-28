/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.activities.composeWearActivity

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.composeActivity.COMPOSE_KOTLIN_COMPILER_VERSION
import com.android.tools.idea.wizard.template.impl.activities.composeActivity.COMPOSE_UI_VERSION
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.src.app_package.mainActivityKt
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.src.app_package.theme.colorKt
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.src.app_package.theme.themeKt
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.src.app_package.theme.typeKt
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.res.values_round.stringsXml as stringsRoundXml

fun RecipeExecutor.composeWearActivityRecipe(
    moduleData: ModuleTemplateData,
    activityClass: String,
    packageName: String,
    isLauncher: Boolean,
    greeting: String,
    wearAppName: String,
    defaultPreview: String
) {
    val (_, srcOut, resOut, manifestOut) = moduleData
    addAllKotlinDependencies(moduleData)

    val composeVersionVarName = getDependencyVarName("androidx.compose.ui:ui", "compose_version")
    val wearComposeVersionVarName =
        getDependencyVarName("androidx.wear.compose:compose-material", "wear_compose_version")
    setExtVar(composeVersionVarName, COMPOSE_UI_VERSION)
    setExtVar(wearComposeVersionVarName, "1.0.0")

    // Note: Compose versioning is per group. "androidx.compose.ui:ui" group has its own variable
    addDependency(mavenCoordinate = "androidx.compose.ui:ui:\${$composeVersionVarName}")
    addDependency(mavenCoordinate = "androidx.wear.compose:compose-material:\${$wearComposeVersionVarName}")
    addDependency(mavenCoordinate = "androidx.wear.compose:compose-foundation:\${$wearComposeVersionVarName}")
    addDependency(
        mavenCoordinate = "androidx.compose.ui:ui-tooling:\${$composeVersionVarName}",
        configuration = "debugImplementation"
    )
    addDependency(mavenCoordinate = "androidx.compose.ui:ui-tooling-preview:\${$composeVersionVarName}")
    addDependency(mavenCoordinate = "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")
    addDependency(mavenCoordinate = "androidx.activity:activity-compose:1.3.1")
    addDependency(
        mavenCoordinate = "androidx.compose.ui:ui-test-manifest:\${$composeVersionVarName}",
        configuration = "debugImplementation"
    )
    addDependency(
        mavenCoordinate = "androidx.compose.ui:ui-test-junit4:\${$composeVersionVarName}",
        configuration = "androidTestImplementation"
    )
    generateManifest(
        moduleData = moduleData,
        activityClass = "presentation.${activityClass}",
        activityThemeName = "@android:style/Theme.DeviceDefault",
        packageName = packageName,
        isLauncher = isLauncher,
        hasNoActionBar = true,
        generateActivityTitle = true
    )
    mergeXml(androidManifestWearOsAdditions(), manifestOut.resolve("AndroidManifest.xml"))
    mergeXml(
        stringsXml(
            activityClass,
            moduleData.isNewModule
        ), resOut.resolve("values/strings.xml")
    )
    mergeXml(stringsRoundXml(), resOut.resolve("values-round/strings.xml"))

    val themeName = "${moduleData.themesData.appName}Theme"
    save(
        mainActivityKt(
            activityClass,
            defaultPreview,
            greeting,
            wearAppName,
            packageName,
            themeName
        ),
        srcOut.resolve("presentation/${activityClass}.kt")
    )
    val uiThemeFolder = "presentation/theme"
    save(colorKt(packageName), srcOut.resolve("$uiThemeFolder/Color.kt"))
    save(typeKt(packageName), srcOut.resolve("$uiThemeFolder/Type.kt"))
    save(themeKt(packageName, themeName), srcOut.resolve("$uiThemeFolder/Theme.kt"))

    requireJavaVersion("1.8", true)
    setBuildFeature("compose", true)
    // Note: kotlinCompilerVersion default is declared in TaskManager.COMPOSE_KOTLIN_COMPILER_VERSION
    setComposeOptions(kotlinCompilerExtensionVersion = COMPOSE_KOTLIN_COMPILER_VERSION)

    open(srcOut.resolve("${activityClass}.kt"))
}
