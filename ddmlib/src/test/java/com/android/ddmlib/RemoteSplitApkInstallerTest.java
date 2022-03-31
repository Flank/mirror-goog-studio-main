/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.ddmlib.IntegrationTest.getPathToAdb;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.PackageManager;
import com.android.sdklib.AndroidVersion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import junit.framework.TestSuite;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RemoteSplitApkInstaller}. */
@RunWith(JUnit4.class)
public class RemoteSplitApkInstallerTest extends TestSuite {

    private List<String> remoteApkPaths =
            Arrays.asList("/data/local/tmp/foo.apk", "/data/local/tmp/foo.dm");
    private List<String> installOptions = Arrays.asList("-d");
    private Long timeout = 1800L;
    private TimeUnit timeUnit = TimeUnit.SECONDS;

    public static final String SERIAL = "test_device_001";
    public static final String MANUFACTURER = "Google";
    public static final String MODEL = "Nexus Silver";
    public static final String RELEASE = "8.0";
    private FakeAdbServer myServer;

    @Before
    public void setUp() throws IOException {
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.installDefaultCommandHandlers();
        myServer = builder.build();
        // Start server execution.
        myServer.start();
        // Test that we obtain 1 device via the ddmlib APIs
        AndroidDebugBridge.enableFakeAdbServerMode(myServer.getPort());
        AndroidDebugBridge.initIfNeeded(false);
        AndroidDebugBridge bridge =
                AndroidDebugBridge.createBridge(getPathToAdb().toString(), false);
        assertNotNull("Debug bridge", bridge);
    }

    @After
    public void tearDown() {
        try {
            // mServer can be null if the FakeAdbTestRule is not being used as a rule but instead
            // as a helper class to setup Adb. This is sometimes done when test want to control the
            // timing of when an adb server is started / stopped
            if (myServer != null) {
                myServer.stop();
                myServer.awaitServerTermination(1000, TimeUnit.MILLISECONDS);
            }
            AndroidDebugBridge.terminate();
        } catch (InterruptedException ex) {
            // disregard
        }
    }

    public IDevice connectDevice(int apiLevel) throws Throwable {

        CountDownLatch deviceLatch = new CountDownLatch(1);
        AndroidDebugBridge.IDeviceChangeListener deviceListener =
                new AndroidDebugBridge.IDeviceChangeListener() {
                    @Override
                    public void deviceConnected(@NonNull IDevice device) {
                        deviceLatch.countDown();
                    }

                    @Override
                    public void deviceDisconnected(@NonNull IDevice device) {}

                    @Override
                    public void deviceChanged(@NonNull IDevice device, int changeMask) {}
                };
        AndroidDebugBridge.addDeviceChangeListener(deviceListener);
        DeviceState state =
                myServer.connectDevice(
                                SERIAL,
                                MANUFACTURER,
                                MODEL,
                                RELEASE,
                                Integer.toString(apiLevel),
                                DeviceState.HostConnectionType.USB)
                        .get();
        assertThat(deviceLatch.await(5000, TimeUnit.MILLISECONDS)).isTrue();
        AndroidDebugBridge.removeDeviceChangeListener(deviceListener);
        state.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);

        IDevice device = AndroidDebugBridge.getBridge().getDevices()[0];
        device.getProperty("foo");
        long waitUntil = System.currentTimeMillis() + 2000;
        while (!device.arePropertiesSet()) {
            if (System.currentTimeMillis() > waitUntil) {
                throw new IllegalStateException("Unable to retrieve device propeties");
            }
        }

        return device;
    }

    @Test
    public void testInstall() throws Throwable {
        IDevice device = connectDevice(30);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(device, remoteApkPaths, false, installOptions);
        installer.install(timeout, timeUnit);
    }

    @Test
    public void testCreateWithApiLevelException() throws Throwable {
        int unsupportedAPI = AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel() - 1;

        try {
            IDevice device = connectDevice(unsupportedAPI);
            RemoteSplitApkInstaller.create(device, remoteApkPaths, false, installOptions);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            //expected
        }
    }

    @Test
    public void testCreateWithArgumentException() throws Throwable {
        try {
            IDevice device = connectDevice(30);
            RemoteSplitApkInstaller installer =
                    RemoteSplitApkInstaller.create(device, Arrays.asList(), false, installOptions);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testCreateMultiInstallSession() throws Throwable {
        IDevice device = connectDevice(30);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(device, remoteApkPaths, false, installOptions);
        Assert.assertEquals(
                "1234", installer.createMultiInstallSession("-r -d", timeout, timeUnit));
    }

    @Test
    public void testCreateMultiInstallSessionNoSessionId() throws Throwable {
        IDevice device = connectDevice(24);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(device, remoteApkPaths, false, installOptions);
        try {
            installer.createMultiInstallSession(PackageManager.BAD_FLAG, timeout, timeUnit);
            Assert.fail("InstallException expected");
        } catch (InstallException e) {
            //expected
        }
    }

    @Test
    public void testWriteRemoteApk() throws Throwable {
        IDevice device = connectDevice(30);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(device, remoteApkPaths, false, installOptions);
        Assert.assertTrue(
                installer.writeRemoteApk("123456", remoteApkPaths.get(0), timeout, timeUnit));
    }

    @Test
    public void testWriteRemoteApkFailure() throws Throwable {
        IDevice device = connectDevice(30);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(device, remoteApkPaths, false, installOptions);
        Assert.assertFalse(
                installer.writeRemoteApk(
                        PackageManager.BAD_SESSION, remoteApkPaths.get(0), timeout, timeUnit));
    }

    @Test
    public void testInstallCommit() throws Throwable {
        IDevice device = connectDevice(24);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(device, remoteApkPaths, false, installOptions);
        installer.installCommit("12345", timeout, timeUnit);
    }

    @Test
    public void testInstallCommitFailure() throws Throwable {
        IDevice device = connectDevice(30);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(device, remoteApkPaths, false, installOptions);
        try {
            installer.installCommit(PackageManager.BAD_SESSION, timeout, timeUnit);
            Assert.fail("InstallException expected");
        } catch (InstallException e) {
            //expected
        }
    }

    @Test
    public void testInstallAbandon() throws Throwable {
        IDevice device = connectDevice(24);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(device, remoteApkPaths, false, installOptions);
        installer.installAbandon("12345", timeout, timeUnit);
    }

    @Test
    public void testGetOptions() throws Throwable {
        IDevice device = connectDevice(24);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(device, remoteApkPaths, false, installOptions);
        Assert.assertEquals("-r -d", SplitApkInstallerBase.getOptions(true, installOptions));
        Assert.assertEquals("-d", SplitApkInstallerBase.getOptions(false, installOptions));
        Assert.assertEquals("-r", SplitApkInstallerBase.getOptions(true, new ArrayList<String>()));
        Assert.assertEquals("", SplitApkInstallerBase.getOptions(false, new ArrayList<String>()));
        Assert.assertEquals(
                "-r -d", SplitApkInstallerBase.getOptions(true, false, "123", installOptions));
        Assert.assertEquals(
                "-r -p 123 -d",
                SplitApkInstallerBase.getOptions(true, true, "123", installOptions));

        List<String> extraOptions = new ArrayList<>(installOptions);
        extraOptions.add("-x");
        Assert.assertEquals("-r -d -x", SplitApkInstallerBase.getOptions(true, extraOptions));
        Assert.assertEquals(
                "-r -p 123 -d -x",
                SplitApkInstallerBase.getOptions(true, true, "123", extraOptions));
    }

    @Test
    public void testGetInstallPmPrefix() throws Throwable {
        IDevice device = connectDevice(23);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(device, remoteApkPaths, false, installOptions);
        Assert.assertEquals(AdbHelper.AdbService.SHELL, installer.getService());
        Assert.assertEquals("pm", installer.getPrefix());
    }

    @Test
    public void testGetInstallCmdPrefix() throws Throwable {
        IDevice device = connectDevice(24);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(device, remoteApkPaths, false, installOptions);
        Assert.assertEquals(AdbHelper.AdbService.SHELL, installer.getService());
        Assert.assertEquals("cmd package", installer.getPrefix());
    }

    @Test
    public void testGetInstallAbbExecPrefix() throws Throwable {
        IDevice device = connectDevice(30);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(device, remoteApkPaths, false, installOptions);
        Assert.assertEquals(AdbHelper.AdbService.ABB_EXEC, installer.getService());
        Assert.assertEquals("package", installer.getPrefix());
    }

}
