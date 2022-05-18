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

package com.android.tools.layoutinspector.errors

import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.ErrorCode
import java.lang.RuntimeException

fun noHardwareAcceleration() = InspectorError(
    "Activity must be hardware accelerated for live inspection",
    ErrorCode.NO_HARDWARE_ACCELERATION
)

fun noRootViews() = InspectorError(
    "Unable to find any root Views",
    ErrorCode.NO_ROOT_VIEWS_FOUND
)

val Throwable.errorCode: ErrorCode
  get() = (this as? InspectorError)?.code ?: ErrorCode.UNKNOWN_ERROR_CODE

/**
 * An exception class with a [message] and an error [code].
 *
 * The error [code] can be used for analytics reporting.
 */
class InspectorError(message: String, val code: ErrorCode, cause: Exception? = null) :
    RuntimeException(message, cause)
