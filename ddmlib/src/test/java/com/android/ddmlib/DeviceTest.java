/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.ddmlib.Device.InstallReceiver;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.easymock.IAnswer;

/** Unit tests for {@link Device}. */
public class DeviceTest extends TestCase {
    public void testScreenRecorderOptions() {
        ScreenRecorderOptions options =
                new ScreenRecorderOptions.Builder()
                        .setBitRate(6)
                        .setSize(600,400)
                        .build();
        assertEquals("screenrecord --size 600x400 --bit-rate 6000000 /sdcard/1.mp4",
                Device.getScreenRecorderCommand("/sdcard/1.mp4", options));

        options = new ScreenRecorderOptions.Builder().setTimeLimit(100, TimeUnit.SECONDS).build();
        assertEquals("screenrecord --time-limit 100 /sdcard/1.mp4",
                Device.getScreenRecorderCommand("/sdcard/1.mp4", options));

        options = new ScreenRecorderOptions.Builder().setTimeLimit(4, TimeUnit.MINUTES).build();
        assertEquals("screenrecord --time-limit 180 /sdcard/1.mp4",
                Device.getScreenRecorderCommand("/sdcard/1.mp4", options));
    }

    /** Helper method that sets the mock device to return the given response on a shell command */
    @SuppressWarnings("unchecked")
    static void injectShellResponse(IDevice mockDevice, final String response) throws Exception {
        IAnswer<Object> shellAnswer = new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                // insert small delay to simulate latency
                Thread.sleep(50);
                IShellOutputReceiver receiver =
                    (IShellOutputReceiver)EasyMock.getCurrentArguments()[1];
                byte[] inputData = response.getBytes();
                receiver.addOutput(inputData, 0, inputData.length);
                return null;
            }
        };
        mockDevice.executeShellCommand(EasyMock.<String>anyObject(),
                    EasyMock.<IShellOutputReceiver>anyObject(),
                    EasyMock.anyLong(), EasyMock.<TimeUnit>anyObject());
        EasyMock.expectLastCall().andAnswer(shellAnswer);
    }

    /** Helper method that sets the mock device to throw the given exception on a shell command */
    static void injectShellExceptionResponse(@NonNull IDevice mockDevice, @NonNull Throwable e)
            throws Exception {
        mockDevice.executeShellCommand(EasyMock.<String>anyObject(),
                EasyMock.<IShellOutputReceiver>anyObject(),
                EasyMock.anyLong(), EasyMock.<TimeUnit>anyObject());
        EasyMock.expectLastCall().andThrow(e);
    }

    /** Helper method that creates a mock device. */
    static IDevice createMockDevice() {
        IDevice mockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("serial");
        EasyMock.expect(mockDevice.isOnline()).andStubReturn(Boolean.TRUE);
        return mockDevice;
    }

    /**
     * Test that {@link InstallReceiver} properly reports different states based on install output.
     */
    public void testInstallReceiver() {
        InstallReceiver receiver = new InstallReceiver();
        // Check default message is not empty.
        assertEquals("Did not receive any ouput from command.", receiver.getErrorMessage());
        // In case of success, the error message is null.
        receiver.processNewLines(new String[] {"Success"});
        assertNull(receiver.getErrorMessage());
        // In case of recognized failure, the error message captures it.
        receiver.processNewLines(new String[] {"Failure [INSTALL_ERROR oups i failed]"});
        assertEquals("INSTALL_ERROR oups i failed", receiver.getErrorMessage());
        // In case of non-recognized failure, special error message.
        receiver.processNewLines(new String[] {"Well it didn't go as planned"});
        assertEquals("Unknown failure (Well it didn't go as planned)", receiver.getErrorMessage());
    }
}
