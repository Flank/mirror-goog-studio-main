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

package com.android.sdklib.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.testutils.MockLog;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link AvdManagerCli}
 *
 * <p>TODO: tests for command-line input
 */
public class AvdManagerCliTest {
    private static final String SDK_LOCATION = "/sdk";
    private static final String AVD_LOCATION = "/avd";

    private MockFileOp mFileOp;
    private AndroidSdkHandler mSdkHandler;
    private MockLog mLogger;
    private AvdManagerCli mCli;
    private AvdManager mAvdManager;
    private ISystemImage mWearImage;
    private ISystemImage mGapiImage;

    @Before
    public void setUp() throws Exception {
        mFileOp = new MockFileOp();
        RepositoryPackages packages = new RepositoryPackages();
        String gApiPath = "system-images;android-25;google_apis;x86";
        FakePackage.FakeLocalPackage p1 = new FakePackage.FakeLocalPackage(gApiPath);
        DetailsTypes.SysImgDetailsType details1 =
                AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
        details1.setTag(IdDisplay.create("google_apis", "Google APIs"));
        details1.setAbi("x86");
        details1.setVendor(IdDisplay.create("google", "Google"));
        details1.setApiLevel(25);
        p1.setTypeDetails((TypeDetails) details1);
        p1.setInstalledPath(new File(SDK_LOCATION, "25-gapi-x86"));
        mFileOp.recordExistingFile(new File(p1.getLocation(), SystemImageManager.SYS_IMG_NAME));
        mFileOp.recordExistingFile(new File(p1.getLocation(), AvdManager.USERDATA_IMG));
        String wearPath = "system-images;android-26;android-wear;armeabi-v7a";
        FakePackage.FakeLocalPackage p2 = new FakePackage.FakeLocalPackage(wearPath);
        DetailsTypes.SysImgDetailsType details2 =
                AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
        details2.setTag(IdDisplay.create("android-wear", "Google APIs"));
        details2.setAbi("armeabi-v7a");
        details2.setApiLevel(26);
        p2.setTypeDetails((TypeDetails) details2);
        p2.setInstalledPath(new File(SDK_LOCATION, "26-wear-arm"));
        mFileOp.recordExistingFile(new File(p2.getLocation(), SystemImageManager.SYS_IMG_NAME));
        mFileOp.recordExistingFile(new File(p2.getLocation(), AvdManager.USERDATA_IMG));

        packages.setLocalPkgInfos(ImmutableList.of(p1, p2));

        RepoManager mgr = new FakeRepoManager(new File(SDK_LOCATION), packages);

        mSdkHandler =
                new AndroidSdkHandler(new File(SDK_LOCATION), new File(AVD_LOCATION), mFileOp, mgr);
        mLogger = new MockLog();
        mCli = new AvdManagerCli(mLogger, mSdkHandler, SDK_LOCATION, AVD_LOCATION, null);
        mAvdManager = AvdManager.getInstance(mSdkHandler, new File(AVD_LOCATION), mLogger);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        SystemImageManager systemImageManager = mSdkHandler.getSystemImageManager(progress);
        mWearImage =
                systemImageManager.getImageAt(
                        mSdkHandler.getLocalPackage(wearPath, progress).getLocation());
        mGapiImage =
                systemImageManager.getImageAt(
                        mSdkHandler.getLocalPackage(gApiPath, progress).getLocation());
    }

    @Test
    public void createAvd() throws Exception {
        mCli.run(
                new String[] {
                    "create",
                    "avd",
                    "--name",
                    "testAvd",
                    "-k",
                    "system-images;android-26;android-wear;armeabi-v7a",
                    "-d",
                    "Nexus 6P"
                });
        mAvdManager.reloadAvds(mLogger);
        AvdInfo info = mAvdManager.getAvd("testAvd", true);
        assertEquals("armeabi-v7a", info.getAbiType());
        assertEquals("Google", info.getDeviceManufacturer());
        assertEquals(new AndroidVersion(26, null), info.getAndroidVersion());
        assertEquals(mWearImage, info.getSystemImage());
    }

    @Test
    public void deleteAvd() throws Exception {
        mAvdManager.createAvd(
                new File(AVD_LOCATION, "test1"),
                "testAvd1",
                mGapiImage,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                mLogger);
        mAvdManager.createAvd(
                new File(AVD_LOCATION, "test2"),
                "testAvd2",
                mWearImage,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                mLogger);
        mAvdManager.reloadAvds(mLogger);
        assertEquals(2, mAvdManager.getAllAvds().length);

        mCli.run(new String[] {"delete", "avd", "--name", "testAvd1"});

        mAvdManager.reloadAvds(mLogger);
        assertEquals(1, mAvdManager.getAllAvds().length);

        AvdInfo info = mAvdManager.getAvd("testAvd2", true);
        assertNotNull(info);
    }

    @Test
    public void moveAvd() throws Exception {
        mAvdManager.createAvd(
                new File(AVD_LOCATION, "test1"),
                "testAvd1",
                mGapiImage,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                mLogger);
        File moved = new File(AVD_LOCATION, "moved");
        mCli.run(
                new String[] {
                    "move",
                    "avd",
                    "--name",
                    "testAvd1",
                    "-p",
                    moved.getAbsolutePath(),
                    "-r",
                    "newName"
                });
        mAvdManager.reloadAvds(mLogger);
        assertEquals(1, mAvdManager.getAllAvds().length);

        AvdInfo info = mAvdManager.getAvd("newName", true);
        assertEquals(moved.getAbsolutePath(), info.getDataFolderPath());
    }

    @Test
    public void listAvds() throws Exception {
        mAvdManager.createAvd(
                new File(AVD_LOCATION, "test1"),
                "testAvd1",
                mGapiImage,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                mLogger);
        mAvdManager.createAvd(
                new File(AVD_LOCATION, "test2"),
                "testAvd2",
                mWearImage,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                mLogger);
        mAvdManager.reloadAvds(mLogger);
        mCli.run(new String[] {"list", "avds"});
        assertEquals(
                "P Available Android Virtual Devices:\n"
                        + "P     Name: testAvd1\n"
                        + "P     Path: "
                        + new File("/avd/test1").getAbsolutePath()
                        + "\n"
                        + "P   Target: Google APIs (Google)\n"
                        + "P           Based on: Android 7.1.1 (Nougat)"
                        + "P  Tag/ABI: google_apis/x86\n"
                        + "P ---------\n"
                        + "P     Name: testAvd2\n"
                        + "P     Path: "
                        + new File("/avd/test2").getAbsolutePath()
                        + "\n"
                        + "P   Target: Google APIs\n"
                        + "P           Based on: Android 8.0 (O)"
                        + "P  Tag/ABI: android-wear/armeabi-v7a\n",
                Joiner.on("").join(mLogger.getMessages()));
    }

    @Test
    public void listTargets() throws Exception {
        RepoManager repoManager = mSdkHandler.getSdkManager(new FakeProgressIndicator());

        FakePackage.FakeLocalPackage p1 = new FakePackage.FakeLocalPackage("platforms;android-25");
        DetailsTypes.PlatformDetailsType details1 =
                AndroidSdkHandler.getRepositoryModule()
                        .createLatestFactory()
                        .createPlatformDetailsType();
        details1.setApiLevel(25);
        p1.setTypeDetails((TypeDetails) details1);
        p1.setInstalledPath(new File(SDK_LOCATION, "platforms/android-25"));
        mFileOp.recordExistingFile(new File(p1.getLocation(), SdkConstants.FN_BUILD_PROP));
        FakePackage.FakeLocalPackage p2 = new FakePackage.FakeLocalPackage("platforms;android-O");
        DetailsTypes.PlatformDetailsType details2 =
                AndroidSdkHandler.getRepositoryModule()
                        .createLatestFactory()
                        .createPlatformDetailsType();
        details2.setApiLevel(25);
        details2.setCodename("O");
        p2.setTypeDetails((TypeDetails) details2);
        p2.setInstalledPath(new File(SDK_LOCATION, "platforms/android-O"));
        mFileOp.recordExistingFile(new File(p2.getLocation(), SdkConstants.FN_BUILD_PROP));

        repoManager.getPackages().setLocalPkgInfos(ImmutableList.of(p1, p2));

        mCli.run(new String[] {"list", "targets"});
        assertEquals(
                "P Available Android targets:\n"
                        + "P ----------\n"
                        + "P id: 1 or \"android-25\"\n"
                        + "P      Name: Android API 25\n"
                        + "P      Type: Platform\n"
                        + "P      API level: 25\n"
                        + "P      Revision: 1\n"
                        + "P ----------\n"
                        + "P id: 2 or \"android-O\"\n"
                        + "P      Name: Android API 25, O preview (Preview)\n"
                        + "P      Type: Platform\n"
                        + "P      API level: O\n"
                        + "P      Revision: 1\n",
                Joiner.on("").join(mLogger.getMessages()));
    }

    @Test
    public void listDevices() throws Exception {
        mCli.run(new String[] {"list", "devices", "-c"});
        assertEquals(
                ImmutableList.of(
                        "P tv_1080p\n",
                        "P tv_720p\n",
                        "P wear_round\n",
                        "P wear_round_chin_320_290\n",
                        "P wear_square\n",
                        "P Galaxy Nexus\n",
                        "P Nexus 10\n",
                        "P Nexus 4\n",
                        "P Nexus 5\n",
                        "P Nexus 5X\n",
                        "P Nexus 6\n",
                        "P Nexus 6P\n",
                        "P Nexus 7 2013\n",
                        "P Nexus 7\n",
                        "P Nexus 9\n",
                        "P Nexus One\n",
                        "P Nexus S\n",
                        "P pixel\n",
                        "P pixel_c\n",
                        "P pixel_xl\n",
                        "P 2.7in QVGA\n",
                        "P 2.7in QVGA slider\n",
                        "P 3.2in HVGA slider (ADP1)\n",
                        "P 3.2in QVGA (ADP2)\n",
                        "P 3.3in WQVGA\n",
                        "P 3.4in WQVGA\n",
                        "P 3.7 FWVGA slider\n",
                        "P 3.7in WVGA (Nexus One)\n",
                        "P 4in WVGA (Nexus S)\n",
                        "P 4.65in 720p (Galaxy Nexus)\n",
                        "P 4.7in WXGA\n",
                        "P 5.1in WVGA\n",
                        "P 5.4in FWVGA\n",
                        "P 7in WSVGA (Tablet)\n",
                        "P 10.1in WXGA (Tablet)\n"),
                mLogger.getMessages()
                        .stream()
                        .filter(s -> s.startsWith("P"))
                        .collect(Collectors.toList()));
        assertTrue(mLogger.getMessages().contains("P wear_round_chin_320_290\n"));
        assertTrue(mLogger.getMessages().contains("P Nexus 6P\n"));
        assertTrue(mLogger.getMessages().contains("P tv_1080p\n"));
        mLogger.clear();
        mCli = new AvdManagerCli(mLogger, mSdkHandler, SDK_LOCATION, AVD_LOCATION, null);
        mCli.run(new String[] {"list", "devices"});
        assertTrue(
                Joiner.on("")
                        .join(mLogger.getMessages())
                        .contains(
                                "P ---------\n"
                                        + "P id: 28 or \"4in WVGA (Nexus S)\"\n"
                                        + "P     Name: 4\" WVGA (Nexus S)\n"
                                        + "P     OEM : Generic\n"
                                        + "P ---------\n"
                                        + "P id: 29 or \"4.65in 720p (Galaxy Nexus)\"\n"
                                        + "P     Name: 4.65\" 720p (Galaxy Nexus)\n"
                                        + "P     OEM : Generic\n"
                                        + "P ---------"));
    }

    @Test
    public void packageHelp() throws Exception {
        try {
            mCli.run(new String[] {"create", "avd", "--name", "testAvd", "-d", "Nexus 6P"});
            fail("Expected exception");
        } catch (Exception expected) {
            // expected
        }

        assertEquals(
                "E Package path (-k) not specified. Valid system image paths are:\n"
                        + "system-images;android-26;android-wear;armeabi-v7a\n"
                        + "system-images;android-25;google_apis;x86",
                Joiner.on("").join(mLogger.getMessages()));
        mLogger.clear();
        try {
            mCli.run(
                    new String[] {
                        "create", "avd", "--name", "testAvd", "-d", "Nexus 6P", "-k", "foo"
                    });
            fail("Expected exception");
        } catch (Exception expected) {
            // expected
        }

        assertEquals(
                "E Package path is not valid. Valid system image paths are:\n"
                        + "system-images;android-26;android-wear;armeabi-v7a\n"
                        + "system-images;android-25;google_apis;x86",
                Joiner.on("").join(mLogger.getMessages()));
    }

    @Test
    public void tagHelp() throws Exception {
        try {
            mCli.run(
                    new String[] {
                        "create",
                        "avd",
                        "--name",
                        "testAvd",
                        "-k",
                        "system-images;android-26;android-wear;armeabi-v7a",
                        "-d",
                        "Nexus 6P",
                        "--tag",
                        "foo"
                    });
            fail("Expected exception");
        } catch (Exception expected) {
            // expected
        }

        assertEquals(
                "E Invalid --tag foo for the selected package. Valid tags are:\n" + "android-wear",
                Joiner.on("").join(mLogger.getMessages()));
    }
}
