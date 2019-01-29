@file:JvmName("ScannerSubjectUtils")

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

package com.android.build.gradle.integration.common.truth

import com.google.common.truth.Fact
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth
import java.util.Scanner

class ScannerSubject(failureMetadata: FailureMetadata,
    private val subject: Scanner) : Subject<ScannerSubject, Scanner>(failureMetadata, subject) {

    companion object {

        fun scanners(): Subject.Factory<ScannerSubject, Scanner> {
            return Subject.Factory<ScannerSubject, Scanner> { failureMetadata, subject ->
                ScannerSubject(
                    failureMetadata,
                    subject
                )
            }
        }

        @JvmStatic
        fun assertThat(scanner: Scanner): ScannerSubject {
            return Truth.assertAbout(scanners()).that(scanner)
        }
    }

    fun contains(string: String) {
        val requestedLines = string.split("\n").iterator()
        if (!requestedLines.hasNext()) {
            failWithActual(Fact.simpleFact("empty or null parameter"))
        }
        var nextString = requestedLines.next()
        while(subject.hasNextLine()) {
            val nextLine = subject.nextLine()
            while (nextLine.contains(nextString)) {
                if (requestedLines.hasNext()) {
                    nextString = requestedLines.next()
                } else {
                    return
                }
            }
        }
        failWithoutActual("contains $string")
    }

    fun doesNotContain(string: String) {
        val requestedLines = string.split("\n").iterator()
        if (!requestedLines.hasNext()) {
            failWithActual(Fact.simpleFact("empty or null parameter"))
        }
        var nextString = requestedLines.next()
        while(subject.hasNextLine()) {
            if (subject.nextLine().contains(nextString)) {
                if (requestedLines.hasNext()) {
                    nextString = requestedLines.next()
                } else {
                    failWithoutActual("does not contain $string")
                }
            }
        }
    }
}

/**
 * Invokes passed action on each line and close the Scanner instance.
 *
 * @param action the lambda to run on each line.
 */
fun Scanner.forEachLine(action: (String) -> Unit) {
    this.use {
        while (it.hasNextLine()) {
            action(it.nextLine())
        }
    }
}