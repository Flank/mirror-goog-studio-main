/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.google.common.truth.Truth.assertThat
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Tests for [AndroidVariantTask]
 */
class AndroidVariantTaskTest {

    @get:Rule
    val temporaryFolder= TemporaryFolder()

    private val called= AtomicBoolean(false)
    private val incrementalCalled= AtomicBoolean(false)
    lateinit var task: TestTask

    open class TestTask @Inject constructor(
        private val called: AtomicBoolean,
        private val incrementalCalled: AtomicBoolean): AndroidVariantTask() {

        fun <E: Exception> action() {
            super.recordTaskAction<E>()
        }

        fun <E: Exception> action(inputs: IncrementalTaskInputs) {
            super.recordTaskAction<E>(inputs)
        }

        override fun recordedTaskAction() {
            called.set(true)
        }

        override fun recordedTaskAction(inputs: IncrementalTaskInputs) {
            assertThat(inputs).isNotNull()
            incrementalCalled.set(true)
        }
    }

    @Before
    fun setup() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build()
        task= project.tasks.create("test", TestTask::class.java, called, incrementalCalled)
    }


    @Test
    fun testRecordMethodCall() {
        task.action<Exception>()
        assertThat(called.get()).isTrue()
        assertThat(incrementalCalled.get()).isFalse()
    }

    @Test
    fun testIncrementalRecordMethodCall() {

        val inputs= Mockito.mock(IncrementalTaskInputs::class.java)
        task.action<Exception>(inputs)
        assertThat(called.get()).isFalse()
        assertThat(incrementalCalled.get()).isTrue()
    }
}