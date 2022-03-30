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

package com.android.build.gradle.internal

import com.android.builder.utils.SynchronizedFile
import com.android.prefs.AndroidLocationsProvider
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoRule
import org.mockito.junit.MockitoJUnit
import java.lang.Thread.UncaughtExceptionHandler
import java.io.File
import java.util.concurrent.Executors.newCachedThreadPool
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@RunWith(JUnit4::class)
class VirtualManagedDeviceLockManagerTest {

    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var avdFolder: File

    private val executorService = newCachedThreadPool()

    @Mock
    private lateinit var androidLocations: AndroidLocationsProvider

    private lateinit var trackedFile: File

    @Before
    fun setup() {
        avdFolder = tmpFolder.newFolder()
        `when`(androidLocations.gradleAvdLocation).thenReturn(avdFolder.toPath())

        trackedFile = avdFolder.resolve("active_gradle_devices")
    }

    @Test
    fun lock_basicLockUpdatesTrackingCorrectly() {
        val lockManager = VirtualManagedDeviceLockManager(androidLocations, 1, 0L)

        lockManager.lock().use {
            // We can go ahead and read the file here to assure it keeps track of the lock number
            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 1")
        }

        // the lock number should be return after the lock is closed.
        assertThat(trackedFile).exists()
        assertThat(trackedFile).contains("MDLockCount 0")
    }

    @Test
    fun lock_multipleLocksCanRunSimultaneously() {
        val lockManager = VirtualManagedDeviceLockManager(androidLocations, 2, 0L)

        lockManager.lock().use {
            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 1")

            // Run two locks concurrently.
            val thread = executorService.submit() {
                lockManager.lock().use {

                    assertThat(trackedFile).contains("MDLockCount 2")
                }
            }

            // This should not take long or be expensive. So we shouldn't have a long timeout
            thread.get(200, TimeUnit.MILLISECONDS)

            // The lock from the second run should be released
            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 1")
        }

        assertThat(trackedFile).exists()

        assertThat(trackedFile).contains("MDLockCount 0")
    }

    @Test
    fun lock_multipleLocksBlockCorrectly() {
        val lockManager = VirtualManagedDeviceLockManager(androidLocations, 1, 0L)

        lateinit var thread: Future<*>

        lockManager.lock().use {
            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 1")

            // Attempt to run the second lock
            thread = executorService.submit() {
                lockManager.lock().use {

                    // When it eventually runs the lock count should only be 1
                    assertThat(trackedFile).contains("MDLockCount 1")
                }
            }

            // Timeout doesn't matter, since the underlying thread won't complete
            assertThrows(TimeoutException::class.java) {
                thread.get(200, TimeUnit.MILLISECONDS)
            }
        }

        // Now the thread should complete fine.
        thread.get(200, TimeUnit.MILLISECONDS)
    }

    @Test
    fun lock_worksWhenMultipleRequested() {
        val lockManager = VirtualManagedDeviceLockManager(androidLocations, 8, 0L)

        // Attempt to grab most of the locks
        lockManager.lock(6).use { lock ->
            assertThat(lock.lockCount).isEqualTo(6)

            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 6")
        }

        // Expect all locks to be returned
        assertThat(trackedFile).exists()

        assertThat(trackedFile).contains("MDLockCount 0")

        // Attempt to grab all of the locks
        lockManager.lock(8).use { lock ->
            assertThat(lock.lockCount).isEqualTo(8)

            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 8")
        }

        // Expect all locks to be returned
        assertThat(trackedFile).exists()

        assertThat(trackedFile).contains("MDLockCount 0")

        // Attempt to grab more than are available
        lockManager.lock(20).use { lock ->
            assertThat(lock.lockCount).isEqualTo(8)

            assertThat(trackedFile).exists()

            assertThat(trackedFile).contains("MDLockCount 8")
        }

        // Expect all locks to be returned
        assertThat(trackedFile).exists()

        assertThat(trackedFile).contains("MDLockCount 0")
    }
}
