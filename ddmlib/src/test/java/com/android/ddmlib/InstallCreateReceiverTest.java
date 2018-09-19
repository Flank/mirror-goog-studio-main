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

/** Unit tests for {@link InstallCreateReceiver}. */
@RunWith(JUnit4.class)
public class InstallCreateReceiverTest {

    /**
     * Test that {@link InstallCreateReceiver} properly reports different states based on install
     * output.
     */
    @Test
    public void testInstallCreateReceiver() {
        InstallCreateReceiver receiver = new InstallCreateReceiver();
        receiver.processNewLines("Success: created install session [2082011841]".split("\n"));
        assertEquals("2082011841", receiver.getSessionId());
        assertEquals(null, receiver.getErrorMessage());
        receiver.processNewLines(new String[] {"Success"});
        assertNull(receiver.getSessionId());
    }
}
