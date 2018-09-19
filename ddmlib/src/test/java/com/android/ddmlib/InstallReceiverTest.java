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

package com.android.ddmlib;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link InstallReceiver}. */
@RunWith(JUnit4.class)
public class InstallReceiverTest {

    /**
     * Test that {@link InstallReceiver} properly reports different states based on install output.
     */
    @Test
    public void testInstallReceiver() {
        InstallReceiver receiver = new InstallReceiver();
        // Check default error message is null
        assertNull(receiver.getErrorMessage());
        // In case of success, the error message is null.
        receiver.processNewLines(new String[] {"Success"});
        assertNull(receiver.getErrorMessage());
        assertEquals("Success", receiver.getSuccessMessage());
        assertTrue(receiver.isSuccessfullyCompleted());
        // Get the Success message
        receiver.processNewLines(new String[] {"Success: created install session [1741914381]"});
        assertEquals("Success: created install session [1741914381]", receiver.getSuccessMessage());
        assertTrue(receiver.isSuccessfullyCompleted());
        // In case of recognized failure, the error message captures it.
        receiver.processNewLines(new String[] {"Failure [INSTALL_ERROR oups i failed]"});
        assertFalse(receiver.isSuccessfullyCompleted());
        assertNull(receiver.getSuccessMessage());
        assertEquals("INSTALL_ERROR oups i failed", receiver.getErrorMessage());
        assertFalse(receiver.isSuccessfullyCompleted());
        assertNull(receiver.getSuccessMessage());
    }

    /**
     * Test that {@link InstallReceiver} properly reports unknown failure based on install output.
     */
    @Test
    public void testInstallReceiverUnknownFailure() {
        InstallReceiver receiver = new InstallReceiver();
        // In case of non-recognized failure, special error message.
        receiver.processNewLines(new String[] {"Well it didn't go as planned"});
        assertEquals("Unknown failure: Well it didn't go as planned", receiver.getErrorMessage());
        assertNull(receiver.getSuccessMessage());
        assertFalse(receiver.isSuccessfullyCompleted());
    }

    /**
     * Test that {@link InstallReceiver} properly reports unknown failure based on install output.
     */
    @Test
    public void testInstallReceiverUnknownFailureMultiline() {
        InstallReceiver receiver = new InstallReceiver();
        // Get the mutiline error message
        receiver.processNewLines(new String[] {"error line 1", "error line 2"});
        assertEquals(
                String.join("\n", new String[] {"Unknown failure: error line 1", "error line 2"}),
                receiver.getErrorMessage());
        assertNull(receiver.getSuccessMessage());
        assertFalse(receiver.isSuccessfullyCompleted());
    }

    /**
     * Test that {@link InstallReceiver} properly reports different states based on install output,
     * and that {@link InstallReceiver#isSuccessfullyCompleted()} reflect if it was a success or
     * not.
     */
    @Test
    public void testInstallReceiver_timeout() {
        InstallReceiver receiver = new InstallReceiver();
        // Check default message
        assertEquals(null, receiver.getErrorMessage());
        assertEquals(null, receiver.getSuccessMessage());
        assertFalse(receiver.isSuccessfullyCompleted());
        // A timeout would results in nothing seen
        receiver.processNewLines(new String[] {});
        assertEquals(null, receiver.getErrorMessage());
        assertFalse(receiver.isSuccessfullyCompleted());
        // A success should update everything
        receiver.processNewLines(new String[] {"Success"});
        assertEquals(null, receiver.getErrorMessage());
        assertTrue(receiver.isSuccessfullyCompleted());
    }
}
