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

import com.android.sdklib.AndroidVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RemoteSplitApkInstaller}. */
@RunWith(JUnit4.class)
public class RemoteSplitApkInstallerTest extends TestCase {

    private IDevice mMockIDevice;
    private List<String> mRemoteApkPaths;
    private List<String> mInstallOptions;
    private Long mTimeout;
    private TimeUnit mTimeUnit;

    @Before
    public void setUp() throws Exception {
        mMockIDevice = DeviceTest.createMockDevice();
        mRemoteApkPaths = new ArrayList<String>();
        mRemoteApkPaths.add("/data/local/tmp/foo.apk");
        mRemoteApkPaths.add("/data/local/tmp/foo.dm");
        mInstallOptions = new ArrayList<String>();
        mInstallOptions.add("-d");
        mTimeout = new Long(1800);
        mTimeUnit = TimeUnit.SECONDS;
    }

    @Test
    public void testInstall() throws Exception {
        DeviceTest.injectShellResponse(
                mMockIDevice, "Success: created install session [2082011841]\r\n");
        DeviceTest.injectShellResponse(mMockIDevice, "Success\r\n");
        DeviceTest.injectShellResponse(mMockIDevice, "Success\r\n");
        DeviceTest.injectShellResponse(mMockIDevice, "Success\r\n");
        RemoteSplitApkInstaller installer = createInstaller();
        installer.install(mTimeout, mTimeUnit);
    }

    @Test
    public void testInstallWriteFailure() throws Exception {
        DeviceTest.injectShellResponse(
                mMockIDevice, "Success: created install session [2082011841]\r\n");
        DeviceTest.injectShellResponse(mMockIDevice, "Success\r\n");
        DeviceTest.injectShellResponse(mMockIDevice, "Failure [INSTALL_FAILED_INVALID_APK]\r\n");
        DeviceTest.injectShellResponse(mMockIDevice, "Success\r\n");
        RemoteSplitApkInstaller installer = createInstaller();
        try {
            installer.install(mTimeout, mTimeUnit);
            fail("InstallException expected");
        } catch (InstallException e) {
            //expected
        }
    }

    @Test
    public void testCreateWithApiLevelException() throws Exception {
        EasyMock.expect(mMockIDevice.getVersion())
                .andStubReturn(
                        new AndroidVersion(
                                AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel() - 1));
        EasyMock.expectLastCall();
        EasyMock.replay(mMockIDevice);
        try {
            RemoteSplitApkInstaller.create(mMockIDevice, mRemoteApkPaths, true, mInstallOptions);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            //expected
        }
    }

    @Test
    public void testCreateWithArgumentException() throws Exception {
        EasyMock.expect(mMockIDevice.getVersion())
                .andStubReturn(
                        new AndroidVersion(
                                AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel()));
        EasyMock.expectLastCall();
        EasyMock.replay(mMockIDevice);
        try {
            RemoteSplitApkInstaller.create(
                    mMockIDevice, new ArrayList<String>(), true, mInstallOptions);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            //expected
        }
    }

    @Test
    public void testCreateMultiInstallSession() throws Exception {
        DeviceTest.injectShellResponse(
                mMockIDevice, "Success: created install session [2082011841]\r\n");
        RemoteSplitApkInstaller installer = createInstaller();
        assertEquals(
                "2082011841", installer.createMultiInstallSession("-r -d", mTimeout, mTimeUnit));
    }

    @Test
    public void testCreateMultiInstallSessionNoSessionId() throws Exception {
        DeviceTest.injectShellResponse(mMockIDevice, "Success\r\n");
        RemoteSplitApkInstaller installer = createInstaller();
        try {
            installer.createMultiInstallSession("-d", mTimeout, mTimeUnit);
            fail("InstallException expected");
        } catch (InstallException e) {
            //expected
        }
    }

    @Test
    public void testWriteRemoteApk() throws Exception {
        DeviceTest.injectShellResponse(mMockIDevice, "Success\r\n");
        RemoteSplitApkInstaller installer = createInstaller();
        assertTrue(installer.writeRemoteApk("123456", mRemoteApkPaths.get(0), mTimeout, mTimeUnit));
    }

    @Test
    public void testWriteRemoteApkFailure() throws Exception {
        DeviceTest.injectShellResponse(mMockIDevice, "Failure [INSTALL_FAILED_INVALID_APK]\r\n");
        RemoteSplitApkInstaller installer = createInstaller();
        assertFalse(
                installer.writeRemoteApk("123456", mRemoteApkPaths.get(0), mTimeout, mTimeUnit));
    }

    @Test
    public void testInstallCommit() throws Exception {
        DeviceTest.injectShellResponse(mMockIDevice, "Success\r\n");
        RemoteSplitApkInstaller installer = createInstaller();
        installer.installCommit("123456", mTimeout, mTimeUnit);
    }

    @Test
    public void testInstallCommitFailure() throws Exception {
        DeviceTest.injectShellResponse(mMockIDevice, "Failure [INSTALL_FAILED_INVALID_APK]\r\n");
        RemoteSplitApkInstaller installer = createInstaller();
        try {
            installer.installCommit("123456", mTimeout, mTimeUnit);
            fail("InstallException expected");
        } catch (InstallException e) {
            //expected
        }
    }

    @Test
    public void testInstallAbandon() throws Exception {
        DeviceTest.injectShellResponse(mMockIDevice, "Success\r\n");
        RemoteSplitApkInstaller installer = createInstaller();
        installer.installAbandon("123456", mTimeout, mTimeUnit);
    }

    @Test
    public void testGetOptions() throws Exception {
        RemoteSplitApkInstaller installer = createInstaller();
        assertEquals("-r -d", installer.getOptions(true, mInstallOptions));
        assertEquals("-d", installer.getOptions(false, mInstallOptions));
        assertEquals("-r", installer.getOptions(true, new ArrayList<String>()));
        assertEquals("", installer.getOptions(false, new ArrayList<String>()));
        assertEquals("-r -d", installer.getOptions(true, false, "123", mInstallOptions));
        assertEquals("-r -p 123 -d", installer.getOptions(true, true, "123", mInstallOptions));
        mInstallOptions.add("-x");
        assertEquals("-r -d -x", installer.getOptions(true, mInstallOptions));
        assertEquals("-r -p 123 -d -x", installer.getOptions(true, true, "123", mInstallOptions));
    }

    @Test
    public void testGetInstallOldPrefix() throws Exception {
        EasyMock.expect(mMockIDevice.getVersion())
                .andStubReturn(
                        new AndroidVersion(AndroidVersion.BINDER_CMD_AVAILABLE.getApiLevel() - 1));
        EasyMock.expectLastCall();
        EasyMock.replay(mMockIDevice);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(
                        mMockIDevice, mRemoteApkPaths, true, mInstallOptions);
        assertEquals("pm", installer.getPrefix());
    }

    @Test
    public void testGetInstalPrefix() throws Exception {
        EasyMock.expect(mMockIDevice.getVersion())
                .andStubReturn(
                        new AndroidVersion(AndroidVersion.BINDER_CMD_AVAILABLE.getApiLevel()));
        EasyMock.expectLastCall();
        EasyMock.replay(mMockIDevice);
        RemoteSplitApkInstaller installer =
                RemoteSplitApkInstaller.create(
                        mMockIDevice, mRemoteApkPaths, true, mInstallOptions);
        assertEquals("cmd package", installer.getPrefix());
    }

    private RemoteSplitApkInstaller createInstaller() {
        EasyMock.expect(mMockIDevice.getVersion())
                .andStubReturn(
                        new AndroidVersion(
                                AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel()));
        EasyMock.expectLastCall();
        EasyMock.replay(mMockIDevice);
        return RemoteSplitApkInstaller.create(mMockIDevice, mRemoteApkPaths, true, mInstallOptions);
    }
}
