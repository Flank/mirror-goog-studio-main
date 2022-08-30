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
package com.android.ide.common.attribution

/**
 * Enum class for the different possible warnings / informations that
 * can be attached to a task category in Build Analyzer.
 */
enum class BuildAnalyzerTaskCategoryIssue(val taskCategory: TaskCategory, val severity: IssueSeverity) {

    /**
     * Warning for when Resource-Ids are final.
     * BooleanOption android.nonFinalResIds = false.
     */
    NON_FINAL_RES_IDS_DISABLED(TaskCategory.ANDROID_RESOURCES, IssueSeverity.WARNING),

    /**
     * Warning for when non-transitive R classes are disabled.
     * BooleanOption android.nonTransitiveRClass = false.
     */
    NON_TRANSITIVE_R_CLASS_DISABLED(TaskCategory.ANDROID_RESOURCES, IssueSeverity.WARNING),

    /**
     * Informational message for when resource validation is enabled.
     * Ideally this warning would only be shown for debug variant.
     * BooleanOption android.disableResourceValidation = false.
     */
    RESOURCE_VALIDATION_ENABLED(TaskCategory.ANDROID_RESOURCES, IssueSeverity.WARNING),

    /**
     * Informational message to notify user that test sharding can be enabled
     * if multiple devices are connected in parallel to run faster.
     * BooleanOption android.androidTest.shardBetweenDevices = false.
     */
    TEST_SHARDING_DISABLED(TaskCategory.TEST, IssueSeverity.INFO),

    /**
     * Warning that Renderscript APIs are deprecated.
     */
    RENDERSCRIPT_API_DEPRECATED(TaskCategory.RENDERSCRIPT, IssueSeverity.WARNING),

    /**
     * General AIDL information which can be found on DAC .
     */
    AVOID_AIDL_UNNECESSARY_USE(TaskCategory.AIDL, IssueSeverity.INFO),

    /**
     * Non-incremental Java annotation processor warning.
     */
    JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR(TaskCategory.JAVA, IssueSeverity.WARNING)
}

enum class IssueSeverity {
    WARNING,
    INFO
}
