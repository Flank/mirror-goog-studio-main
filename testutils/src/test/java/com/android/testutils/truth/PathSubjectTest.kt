/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.testutils.truth

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

class PathSubjectTest {

    @Test
    fun lastModifiedAndNewerThanTest() {
        val file = Jimfs.newFileSystem(Configuration.unix()).getPath("/test/myFile")
        Files.createDirectories(file.parent)
        Files.write(file, listOf("Test content"))

        val now = FileTime.from(Instant.parse("2018-01-11T12:46:00Z"))
        val tenMinutesAgo = FileTime.from(now.to(TimeUnit.MINUTES) - 10, TimeUnit.MINUTES)

        Files.setLastModifiedTime(file, now)

        PathSubject.assertThat(file).exists()
        PathSubject.assertThat(file).wasModifiedAt(now)
        assertThat(assertFailsWith(AssertionError::class) {
            PathSubject.assertThat(file).wasModifiedAt(tenMinutesAgo)
        }.message).isEqualTo(
                "Not true that </test/myFile> was last modified at " +
                        "<2018-01-11T12:36:00Z>. " +
                        "It was last modified at <2018-01-11T12:46:00Z>")
        PathSubject.assertThat(file).isNewerThan(tenMinutesAgo)
        assertThat(assertFailsWith(AssertionError::class) {
            PathSubject.assertThat(file).isNewerThan(now)
        }.message).isEqualTo(
                "Not true that </test/myFile> was modified after " +
                        "<2018-01-11T12:46:00Z>. " +
                        "It was last modified at <2018-01-11T12:46:00Z>")

        Files.setLastModifiedTime(file, tenMinutesAgo)
        assertThat(assertFailsWith(AssertionError::class) {
            PathSubject.assertThat(file).wasModifiedAt(now)
        }.message).isEqualTo(
                "Not true that </test/myFile> was last modified at " +
                        "<2018-01-11T12:46:00Z>. " +
                        "It was last modified at <2018-01-11T12:36:00Z>")
        assertThat(assertFailsWith(AssertionError::class) {
            PathSubject.assertThat(file).isNewerThan(now)
        }.message).isEqualTo(
                "Not true that </test/myFile> was modified after " +
                        "<2018-01-11T12:46:00Z>. " +
                        "It was last modified at <2018-01-11T12:36:00Z>")
    }

}
