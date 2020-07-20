/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class EmulatorConsoleTest {
    /** Test success case for {@link EmulatorConsole#getEmulatorPort(String)}. */
    @Test
    public void getEmulatorPort() {
        assertEquals(Integer.valueOf(5554), EmulatorConsole.getEmulatorPort("emulator-5554"));
    }

    /**
     * Test {@link EmulatorConsole#getEmulatorPort(String)} when input serial has invalid format.
     */
    @Test
    public void getEmulatorPort_invalid() {
        assertNull(EmulatorConsole.getEmulatorPort("invalidserial"));
    }

    /** Test {@link EmulatorConsole#getEmulatorPort(String)} when port is not a number. */
    @Test
    public void getEmulatorPort_nan() {
        assertNull(EmulatorConsole.getEmulatorPort("emulator-NaN"));
    }

    @Test
    public void processOutputLinesLengthEqualsZero() throws CommandFailedException {
        // Arrange
        String[] lines = {};

        // Act
        try {
            EmulatorConsole.processOutput(lines);
            fail();
        }
        // Assert
        catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void processOutputMatcherMatches() {
        // Arrange
        String[] lines = {
            "allows you to control (e.g. start/stop) the execution of the virtual device",
            "",
            "available sub-commands:",
            "    stop             stop the virtual device",
            "    start            start/restart the virtual device",
            "    status           query virtual device status",
            "    heartbeat        query the heart beat number of the guest system",
            "    rewindaudio      rewind the input audio to the beginning",
            "    name             query virtual device name",
            "    snapshot         state snapshot commands",
            "    pause            pause the virtual device",
            "    hostmicon        activate the host audio input device",
            "    hostmicoff       deactivate the host audio input device",
            "    resume           resume the virtual device",
            "    bugreport        generate bug report info.",
            "    id               query virtual device ID",
            "    windowtype       query virtual device headless or qtwindow",
            "    path             query AVD path",
            "    discoverypath    query AVD discovery path",
            "    snapshotspath    query AVD snapshots path",
            "    snapshotpath     query path to a particular AVD snapshot",
            "",
            "KO:  bad sub-command"
        };

        // Act
        try {
            EmulatorConsole.processOutput(lines);
            fail();
        }
        // Assert
        catch (CommandFailedException exception) {
            assertEquals("bad sub-command", exception.getMessage());
        }
    }

    @Test
    public void processOutputLastLineEqualsOkLinux() throws CommandFailedException {
        // Arrange
        String[] lines = {"/home/juancnuno/.android/avd/Pixel_4_API_30.avd", "OK"};

        // Act
        Object output = EmulatorConsole.processOutput(lines);

        // Assert
        assertEquals("/home/juancnuno/.android/avd/Pixel_4_API_30.avd", output);
    }

    @Test
    public void processOutputLastLineEqualsOkWindows() throws CommandFailedException {
        // Arrange
        String[] lines = {"C:\\Users\\rpaquay\\.android\\avd\\Pixel_2_API_29.avd", "OK"};

        // Act
        Object output = EmulatorConsole.processOutput(lines);

        // Assert
        assertEquals("C:\\Users\\rpaquay\\.android\\avd\\Pixel_2_API_29.avd", output);
    }

    @Test
    public void processOutput() throws CommandFailedException {
        // Arrange
        // See http://b/161150889
        String[] lines = {
            "allows you to control (e.g. start/stop) the execution of the virtual device",
            "",
            "available sub-commands:",
            "    stop             stop the virtual device",
            "    start            start/restart the virtual device",
            "    status           query virtual device status",
            "    heartbeat        query the heart beat number of the guest system",
            "    rewindaudio      rewind the input audio to the beginning",
            "    name             query virtual device name",
            "    snapshot         state snapshot commands",
            "    pause            pause the virtual device",
            "    hostmicon        activate the host audio input device",
            "    hostmicoff       deactivate the host audio input device",
            "    resume           resume the virtual device",
            "    bugreport        generate bug report info.",
            "    id               query virtual device ID",
            "    windowtype       query virtual device headless or qtwindow",
            "    path             query AVD path",
            "    discoverypath    query AVD discovery path",
            "    snapshotspath    query AVD snapshots path",
            "    snapshotpath     query path to a pa"
        };

        // Act
        try {
            EmulatorConsole.processOutput(lines);
            fail();
        }
        // Assert
        catch (IllegalArgumentException ignored) {
        }
    }
}
