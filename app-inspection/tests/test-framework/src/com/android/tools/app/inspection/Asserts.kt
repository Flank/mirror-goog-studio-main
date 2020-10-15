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

@file:JvmName("Asserts")

package com.android.tools.app.inspection

import com.android.tools.app.inspection.AppInspection.AppInspectionResponse
import com.android.tools.app.inspection.AppInspection.CreateInspectorResponse
import com.google.common.truth.Truth.assertThat

fun assertRawResponse(response: AppInspectionResponse, responseContent: ByteArray) {
    assertThat(response.hasRawResponse()).isTrue()
    assertThat(response.rawResponse.content.toByteArray()).isEqualTo(responseContent)
}

fun assertDisposeInspectorResponseStatus(
    response: AppInspectionResponse,
    expected: AppInspectionResponse.Status
) {
    assertThat(response.hasDisposeInspectorResponse()).isTrue()
    assertThat(response.status).isEqualTo(expected)
}

fun assertCreateInspectorResponseStatus(
    response: AppInspectionResponse,
    expected: CreateInspectorResponse.Status
) {
    assertThat(response.hasCreateInspectorResponse()).isTrue()
    assertThat(response.createInspectorResponse.status).isEqualTo(expected)
    if (expected == CreateInspectorResponse.Status.SUCCESS) {
        assertThat(response.status).isEqualTo(AppInspectionResponse.Status.SUCCESS)
    } else {
        assertThat(response.status).isEqualTo(AppInspectionResponse.Status.ERROR)
    }
}

