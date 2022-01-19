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

package com.android.build.gradle.tasks.sync

import com.android.ide.model.sync.Variant
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test

class LibraryVariantModelTaskTest: VariantModelTaskAbstractTest<LibraryVariantModelTask>() {

    @Before
    fun setUp() {
        super.setUp(LibraryVariantModelTask::class.java)
    }

    @Test
    fun testTaskAction() {
        super.testTaskAction(
            given = {
                it.setupModuleTaskInputs()
            },
            expect = {
                Truth.assertThat(it.variantCase).isEqualTo(Variant.VariantCase.LIBRARYVARIANTMODEL)
                it.libraryVariantModel.moduleCommonModel.testModuleFields()
            }
        )
    }
}
