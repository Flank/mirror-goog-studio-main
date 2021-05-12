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

package com.android.ddmlib.internal;

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.RemoteSplitApkInstaller;
import com.android.ddmlib.ScreenRecorderOptions;
import com.android.ddmlib.SplitApkInstaller;
import com.android.sdklib.AndroidVersion;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.easymock.IAnswer;

public class DeviceTest extends TestCase {

    public void testScreenRecorderOptions() {
        ScreenRecorderOptions options =
                new ScreenRecorderOptions.Builder().setBitRate(6).setSize(600, 400).build();
        assertEquals(
                "screenrecord --size 600x400 --bit-rate 6000000 /sdcard/1.mp4",
                DeviceImpl.getScreenRecorderCommand("/sdcard/1.mp4", options));

        options = new ScreenRecorderOptions.Builder().setTimeLimit(100, TimeUnit.SECONDS).build();
        assertEquals(
                "screenrecord --time-limit 100 /sdcard/1.mp4",
                DeviceImpl.getScreenRecorderCommand("/sdcard/1.mp4", options));

        options = new ScreenRecorderOptions.Builder().setTimeLimit(4, TimeUnit.MINUTES).build();
        assertEquals(
                "screenrecord --time-limit 180 /sdcard/1.mp4",
                DeviceImpl.getScreenRecorderCommand("/sdcard/1.mp4", options));
    }

    public void testInstallPackages() throws Exception {
        IDevice mMockDevice = createMockDevice();
        List<File> apks = new ArrayList<File>();
        for (int i = 0; i < 3; i++) {
            File apkFile = File.createTempFile("test", ".apk");
            apks.add(apkFile);
        }
        List<String> installOptions = new ArrayList<String>();
        installOptions.add("-d");
        mMockDevice.installPackages(apks, true, installOptions);
        mMockDevice.installPackages(apks, true, installOptions, 1000L, TimeUnit.MINUTES);
        EasyMock.expect(mMockDevice.getVersion())
                .andStubReturn(
                        new AndroidVersion(
                                AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel()));
        EasyMock.expectLastCall();
        EasyMock.replay(mMockDevice);
        SplitApkInstaller.create(mMockDevice, apks, true, installOptions);
        for (File apkFile : apks) {
            apkFile.delete();
        }
    }

    public void testInstallRemotePackages() throws Exception {
        IDevice mMockDevice = createMockDevice();
        List<String> remoteApkPaths = new ArrayList<String>();
        remoteApkPaths.add("/data/local/tmp/foo.apk");
        remoteApkPaths.add("/data/local/tmp/foo.dm");
        List<String> installOptions = new ArrayList<String>();
        installOptions.add("-d");
        mMockDevice.installRemotePackages(remoteApkPaths, true, installOptions);
        mMockDevice.installRemotePackages(
                remoteApkPaths, true, installOptions, 1000L, TimeUnit.MINUTES);
        EasyMock.expect(mMockDevice.getVersion())
                .andStubReturn(
                        new AndroidVersion(
                                AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel()));
        EasyMock.expectLastCall();
        EasyMock.replay(mMockDevice);
        RemoteSplitApkInstaller.create(mMockDevice, remoteApkPaths, true, installOptions);
    }

    /** Helper method that sets the mock device to return the given response on a shell command */
    @SuppressWarnings("unchecked")
    public static void injectShellResponse(IDevice mockDevice, final String response)
            throws Exception {
        injectShellResponse(mockDevice, response, 50);
    }

    /**
     * Helper method that sets the mock device to return the given response on a shell command. The
     * {@code delayMillis} parameter allows simulating device latency, by delaying the response
     */
    @SuppressWarnings("unchecked")
    public static void injectShellResponse(
            IDevice mockDevice, final String response, int delayMillis) throws Exception {
        IAnswer<Object> shellAnswer =
                () -> {
                    // insert small delay to simulate latency
                    Thread.sleep(delayMillis);
                    IShellOutputReceiver receiver =
                            (IShellOutputReceiver) EasyMock.getCurrentArguments()[1];
                    byte[] inputData = response.getBytes();
                    receiver.addOutput(inputData, 0, inputData.length);
                    receiver.flush();
                    return null;
                };
        mockDevice.executeShellCommand(
                EasyMock.anyObject(),
                EasyMock.anyObject(),
                EasyMock.anyLong(),
                EasyMock.anyObject());
        EasyMock.expectLastCall().andAnswer(shellAnswer);
    }

    /** Helper method that sets the mock device to throw the given exception on a shell command */
    public static void injectShellExceptionResponse(
            @NonNull IDevice mockDevice, @NonNull Throwable e) throws Exception {
        mockDevice.executeShellCommand(
                EasyMock.anyObject(),
                EasyMock.anyObject(),
                EasyMock.anyLong(),
                EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(e);
    }

    /** Helper method that creates a mock device. */
    public static IDevice createMockDevice() {
        IDevice mockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("serial");
        EasyMock.expect(mockDevice.isOnline()).andStubReturn(Boolean.TRUE);
        return mockDevice;
    }
}
