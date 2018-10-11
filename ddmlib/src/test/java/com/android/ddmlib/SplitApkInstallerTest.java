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
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SplitApkInstaller}. */
@RunWith(JUnit4.class)
public class SplitApkInstallerTest extends TestCase {

    private IDevice mMockIDevice;
    private List<File> mLocalApks;
    private List<String> mInstallOptions;
    private Long mTimeout;
    private TimeUnit mTimeUnit;

    @Before
    public void setUp() throws Exception {
        mMockIDevice = DeviceTest.createMockDevice();
        mLocalApks = new ArrayList<File>();
        for (int i = 0; i < 3; i++) {
            File apkFile = File.createTempFile("test", ".apk");
            mLocalApks.add(apkFile);
        }
        mInstallOptions = new ArrayList<String>();
        mInstallOptions.add("-d");
        mTimeout = new Long(1800);
        mTimeUnit = TimeUnit.SECONDS;
    }

    @After
    public void tearDown() throws Exception {
        for (File apkFile : mLocalApks) {
            apkFile.delete();
        }
    }

    @Test
    public void testCreate() throws Exception {
        createInstaller();
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
            SplitApkInstaller.create(mMockIDevice, mLocalApks, true, mInstallOptions);
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
            SplitApkInstaller.create(mMockIDevice, new ArrayList<File>(), true, mInstallOptions);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            //expected
        }
    }

    private SplitApkInstaller createInstaller() {
        EasyMock.expect(mMockIDevice.getVersion())
                .andStubReturn(
                        new AndroidVersion(
                                AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel()));
        EasyMock.expectLastCall();
        EasyMock.replay(mMockIDevice);
        return SplitApkInstaller.create(mMockIDevice, mLocalApks, true, mInstallOptions);
    }
}
