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
package com.android.adblib.tools.tests

import com.android.adblib.tools.InstallException
import com.android.adblib.tools.InstallResult
import com.android.adblib.tools.PMDriver
import com.android.adblib.tools.PMDriver.Companion.parseInstallResult
import com.android.adblib.tools.PMDriver.Companion.parseSessionID
import com.android.adblib.tools.SUCCESS_OUTPUT
import org.junit.Assert
import org.junit.Test

class TestInstallOutput {

    /**
     * Test that [InstallerResult] properly reports unknown failure based on install output.
     */
    @Test
    fun testInstallResult() {
        // Check empty string == SUCCESS
        try {
            parseInstallResult("")
        } catch (e : Exception) {
            Assert.fail("Empty is a valid successful output")
        }

        // Check SUCCESS is SUCCESS
        try {
            parseInstallResult(SUCCESS_OUTPUT)
        } catch (e : Exception) {
            Assert.fail("Empty is a valid successful output")
        }
    }

    @Test
    fun testInstallResultErrorCode() {
        val errorCode = "INSTALL_ERROR_DESC"
        val errorMessage = "$errorCode: oups i failed"
        try {
            parseInstallResult("Failure [$errorMessage]")
        } catch (e : InstallException) {
            Assert.assertEquals(errorCode, e.errorCode)
            Assert.assertEquals(errorMessage, e.errorMessage)
        }

        try {
            parseInstallResult("Failure [$errorCode]")
        } catch (e : InstallException) {
            Assert.assertEquals(errorCode, e.errorCode)
            Assert.assertEquals(errorCode, e.errorMessage)
        }
    }

    /**
     * Test that [CommitResult] properly reports unknown failure based on install output.
     */
    @Test
    fun testCommitResult() {
        // Get the Success message
        val sessionID = "1741914381"
        var id = parseSessionID("Success: created install session [$sessionID]\n")
        Assert.assertEquals(sessionID, id)

        // In case of recognized failure, the error message captures it.
        val errorCode = "INSTALL_ERROR"
        try {
            parseSessionID("Failure [$errorCode: Oops]")
        } catch (e : InstallException) {
            Assert.assertEquals(e.errorCode, errorCode)
        }
    }

    /**
     * Test that [CommitResult] properly parse lines with Line Feed ('\n').
     */
    @Test
    fun testCommitResultWithLF() {
        // Get the Success message
        val sessionID = "1741914381"
        var id = parseSessionID("Success: created install session [$sessionID]\n")
        Assert.assertEquals(sessionID, id)
    }
}
