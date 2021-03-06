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
import static org.junit.Assert.assertNull;
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
import com.android.sdklib.PathFileWrapper;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.testutils.MockLog;
import com.android.testutils.file.InMemoryFileSystems;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link AvdManagerCli}
 *
 * <p>TODO: tests for command-line input
 */
public class AvdManagerCliTest {

    private static final String EMU_LIB_LOCATION =
            InMemoryFileSystems.getPlatformSpecificPath("/sdk/emulator/lib");
    private static final String SDK_LOCATION = InMemoryFileSystems.getPlatformSpecificPath("/sdk");
    private static final String AVD_LOCATION = InMemoryFileSystems.getPlatformSpecificPath("/avd");

    private MockFileOp mFileOp;
    private AndroidSdkHandler mSdkHandler;
    private MockLog mLogger;
    private AvdManagerCli mCli;
    private AvdManager mAvdManager;
    private ISystemImage mGapiImage;

    @Before
    public void setUp() throws Exception {
        mFileOp = new MockFileOp();
        RepositoryPackages packages = new RepositoryPackages();
        String gApiPath = "system-images;android-25;google_apis;x86";
        FakePackage.FakeLocalPackage p1 = new FakePackage.FakeLocalPackage(gApiPath, mFileOp);
        DetailsTypes.SysImgDetailsType details1 =
                AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
        details1.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
        details1.setAbi("x86");
        details1.setVendor(IdDisplay.create("google", "Google"));
        details1.setApiLevel(25);
        p1.setTypeDetails((TypeDetails) details1);
        mFileOp.recordExistingFile(p1.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));
        mFileOp.recordExistingFile(p1.getLocation().resolve(AvdManager.USERDATA_IMG));

        String wearPath = "system-images;android-26;android-wear;armeabi-v7a";
        FakePackage.FakeLocalPackage p2 = new FakePackage.FakeLocalPackage(wearPath, mFileOp);
        DetailsTypes.SysImgDetailsType details2 =
                AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
        details2.getTags().add(IdDisplay.create("android-wear", "Google APIs"));
        details2.setAbi("armeabi-v7a");
        details2.setApiLevel(26);
        p2.setTypeDetails((TypeDetails) details2);
        mFileOp.recordExistingFile(p2.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));
        mFileOp.recordExistingFile(p2.getLocation().resolve(AvdManager.USERDATA_IMG));

        // Create a representative hardware configuration file
        String emuPath = "emulator";
        FakePackage.FakeLocalPackage p3 = new FakePackage.FakeLocalPackage(emuPath, mFileOp);
        File hardwareDefs = new File(EMU_LIB_LOCATION, SdkConstants.FN_HARDWARE_INI);
        createHardwarePropertiesFile(hardwareDefs.getPath());

        packages.setLocalPkgInfos(ImmutableList.of(p1, p2, p3));

        RepoManager mgr = new FakeRepoManager(mFileOp.toPath(SDK_LOCATION), packages);

        mSdkHandler =
                new AndroidSdkHandler(
                        mFileOp.toPath(SDK_LOCATION), mFileOp.toPath(AVD_LOCATION), mFileOp, mgr);
        mLogger = new MockLog();
        mCli = new AvdManagerCli(mLogger, mSdkHandler, SDK_LOCATION, AVD_LOCATION, null);
        mAvdManager = AvdManager.getInstance(mSdkHandler, new File(AVD_LOCATION), mLogger);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        SystemImageManager systemImageManager = mSdkHandler.getSystemImageManager(progress);
        mGapiImage =
                systemImageManager.getImageAt(
                        mSdkHandler.getLocalPackage(gApiPath, progress).getLocation());
    }

    @Test
    public void createAvd() throws Exception {
        mCli.run(
                new String[] {
                    "create", "avd",
                    "--name", "testAvd",
                    "-k", "system-images;android-25;google_apis;x86",
                    "-d", "Nexus 6P"
                });
        mAvdManager.reloadAvds(mLogger);
        AvdInfo info = mAvdManager.getAvd("testAvd", true);
        assertEquals("x86", info.getAbiType());
        assertEquals("Google", info.getDeviceManufacturer());
        assertEquals(new AndroidVersion(25, null), info.getAndroidVersion());
        assertEquals(mGapiImage, info.getSystemImage());

        Path avdConfigFile = mFileOp.toPath(info.getDataFolderPath()).resolve("config.ini");
        assertTrue(
                "Expected config.ini in " + info.getDataFolderPath(), Files.exists(avdConfigFile));
        Map<String, String> config =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertEquals("123", config.get("integerPropName"));
        assertEquals("none", config.get("runtime.network.latency"));
        assertEquals("full", config.get("runtime.network.speed"));
        assertEquals(new Storage(1536, Storage.Unit.MiB), Storage.getStorageFromString(config.get("hw.ramSize")));
        assertEquals(new Storage(512, Storage.Unit.MiB), Storage.getStorageFromString(config.get("sdcard.size")));
        assertEquals(new Storage(384, Storage.Unit.MiB), Storage.getStorageFromString(config.get("vm.heapSize")));
    }

    @Test
    public void deleteAvd() throws Exception {
        mCli.run(
          new String[] {
            "create", "avd",
            "--name", "testAvd1",
            "-k", "system-images;android-25;google_apis;x86",
            "-d", "Nexus 6P"
          });
        mCli.run(
          new String[] {
            "create", "avd",
            "--name", "testAvd2",
            "-k", "system-images;android-26;android-wear;armeabi-v7a",
            "-d", "Nexus 6P"
          });
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
        mCli.run(
          new String[] {
            "create", "avd",
            "--name", "testAvd1",
            "-k", "system-images;android-25;google_apis;x86",
            "-d", "Nexus 6P"
          });
        File moved = new File(AVD_LOCATION, "moved");
        mCli.run(
                new String[] {
                    "move", "avd",
                    "--name", "testAvd1",
                    "-p", moved.getAbsolutePath(),
                    "-r", "newName"
                });
        mAvdManager.reloadAvds(mLogger);
        assertEquals(1, mAvdManager.getAllAvds().length);

        AvdInfo info = mAvdManager.getAvd("newName", true);
        assertEquals(moved.getAbsolutePath(), info.getDataFolderPath());
    }

    @Test
    public void listAvds() throws Exception {
        mCli.run(
          new String[]{
            "create", "avd",
            "--name", "testGapiAvd",
            "-k", "system-images;android-25;google_apis;x86",
            "-d", "Nexus 6P"
          });
        mCli.run(
          new String[]{
            "create", "avd",
            "--name", "testWearApi",
            "-k", "system-images;android-26;android-wear;armeabi-v7a",
            "-d", "wear_round"
          });
        mAvdManager.reloadAvds(mLogger);
        mLogger.clear();
        mCli.run(new String[] {"list", "avds"});
        assertEquals(
                "P Available Android Virtual Devices:\n"
                        + "P     Name: testGapiAvd\n"
                        + "P   Device: Nexus 6PP  (Google)P \n"
                        + "P     Path: "
                        + InMemoryFileSystems.getPlatformSpecificPath("/avd/testGapiAvd.avd")
                        + "\n"
                        + "P   Target: Google APIs (Google)\n"
                        + "P           Based on: Android 7.1.1 (Nougat)P  Tag/ABI: google_apis/x86\n"
                        + "P   Sdcard: 512 MB\n"
                        + "P ---------\n"
                        + "P     Name: testWearApi\n"
                        + "P   Device: wear_roundP  (Google)P \n"
                        + "P     Path: "
                        + InMemoryFileSystems.getPlatformSpecificPath("/avd/testWearApi.avd")
                        + "\n"
                        + "P   Target: Google APIs\n"
                        + "P           Based on: Android 8.0 (Oreo)P  Tag/ABI: android-wear/armeabi-v7a\n"
                        + "P   Sdcard: 512 MB\n",
                Joiner.on("").join(mLogger.getMessages()));
    }

    @Test
    public void listTargets() {
        RepoManager repoManager = mSdkHandler.getSdkManager(new FakeProgressIndicator());

        String p1Path = "platforms;android-25";
        FakePackage.FakeLocalPackage p1 = new FakePackage.FakeLocalPackage(p1Path, mFileOp);
        DetailsTypes.PlatformDetailsType details1 =
                AndroidSdkHandler.getRepositoryModule()
                        .createLatestFactory()
                        .createPlatformDetailsType();
        details1.setApiLevel(25);
        p1.setTypeDetails((TypeDetails) details1);
        mFileOp.recordExistingFile(p1.getLocation().resolve(SdkConstants.FN_BUILD_PROP));
        String p2Path = "platforms;android-O";
        FakePackage.FakeLocalPackage p2 = new FakePackage.FakeLocalPackage(p2Path, mFileOp);
        DetailsTypes.PlatformDetailsType details2 =
                AndroidSdkHandler.getRepositoryModule()
                        .createLatestFactory()
                        .createPlatformDetailsType();
        details2.setApiLevel(25);
        details2.setCodename("O");
        p2.setTypeDetails((TypeDetails) details2);
        mFileOp.recordExistingFile(p2.getLocation().resolve(SdkConstants.FN_BUILD_PROP));

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
    public void listDevices() {
        mCli.run(new String[] {"list", "devices", "-c"});
        assertEquals(
                ImmutableList.of(
                        "P tv_1080p\n",
                        "P tv_720p\n",
                        "P automotive_1024p_landscape\n",
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
                        "P pixel_2\n",
                        "P pixel_2_xl\n",
                        "P pixel_3\n",
                        "P pixel_3_xl\n",
                        "P pixel_3a\n",
                        "P pixel_3a_xl\n",
                        "P pixel_4\n",
                        "P pixel_4_xl\n",
                        "P pixel_4a\n",
                        "P pixel_5\n",
                        "P pixel_c\n",
                        "P pixel_xl\n",
                        "P wear_round\n",
                        "P wear_round_chin_320_290\n",
                        "P wear_square\n",
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
                        "P 6.7in Foldable\n",
                        "P 7in WSVGA (Tablet)\n",
                        "P 7.4in Rollable\n",
                        "P 7.6in Foldable\n",
                        "P 8in Foldable\n",
                        "P 10.1in WXGA (Tablet)\n",
                        "P 13.5in Freeform\n"),
                mLogger.getMessages().stream()
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
                                        + "P id: 39 or \"4in WVGA (Nexus S)\"\n"
                                        + "P     Name: 4\" WVGA (Nexus S)\n"
                                        + "P     OEM : Generic\n"
                                        + "P ---------\n"
                                        + "P id: 40 or \"4.65in 720p (Galaxy Nexus)\"\n"
                                        + "P     Name: 4.65\" 720p (Galaxy Nexus)\n"
                                        + "P     OEM : Generic\n"
                                        + "P ---------"));
    }

    @Test
    public void validateResponse() {
        Path hardwareDefs = mFileOp.toPath(EMU_LIB_LOCATION).resolve(SdkConstants.FN_HARDWARE_INI);
        Map<String, HardwareProperties.HardwareProperty> hwMap =
                HardwareProperties.parseHardwareDefinitions(
                        new PathFileWrapper(hardwareDefs), mLogger);
        HardwareProperties.HardwareProperty[] hwProperties = hwMap.values().toArray(
          new HardwareProperties.HardwareProperty[0]);

        for (HardwareProperties.HardwareProperty aProperty : hwProperties) {
            switch (aProperty.getName()) {
                case "booleanPropName":
                    assertEquals("Boolean 'yes' should be valid",
                               "yes", AvdManagerCli.validateResponse("yes", aProperty, mLogger));
                    assertEquals("Boolean 'Yes' should be valid",
                               "yes", AvdManagerCli.validateResponse("Yes", aProperty, mLogger));
                    assertEquals("Boolean 'no' should be valid",
                               "no", AvdManagerCli.validateResponse("no", aProperty, mLogger));
                    assertNull(
                            "Boolean 'true' should be invalid",
                            AvdManagerCli.validateResponse("true", aProperty, mLogger));
                    assertNull(
                            "Boolean 'maybe' should be invalid",
                            AvdManagerCli.validateResponse("maybe", aProperty, mLogger));
                    break;
                case "integerPropName":
                    assertEquals("Integer '123' should be valid",
                                 "123", AvdManagerCli.validateResponse("123", aProperty, mLogger));
                    assertNull(
                            "Integer '123x' should be invalid",
                            AvdManagerCli.validateResponse("123x", aProperty, mLogger));
                    break;
                case "integerEnumPropName":
                    assertEquals("Integer enum '40' should be valid",
                                 "40", AvdManagerCli.validateResponse("40", aProperty, mLogger));
                    assertNull(
                            "Integer enum '45' should be invalid",
                            AvdManagerCli.validateResponse("45", aProperty, mLogger));
                    break;
                case "stringPropName":
                    assertEquals("String 'Whatever$^*)#?!' should be valid",
                                 "Whatever$^*)#?!", AvdManagerCli.validateResponse("Whatever$^*)#?!", aProperty, mLogger));
                    break;
                case "stringEnumPropName":
                    assertEquals("String enum 'okString0' should be valid",
                                 "okString0", AvdManagerCli.validateResponse("okString0", aProperty, mLogger));
                    assertNull(
                            "String enum 'okString3' should be invalid",
                            AvdManagerCli.validateResponse("okString3", aProperty, mLogger));
                    break;
                case "stringEnumTemplatePropName":
                    assertEquals("String enum 'fixedString' should be valid",
                                 "fixedString", AvdManagerCli.validateResponse("fixedString", aProperty, mLogger));
                    assertEquals("String enum 'extensibleString0' should be valid",
                                 "extensibleString0", AvdManagerCli.validateResponse("extensibleString0", aProperty, mLogger));
                    assertEquals("String enum 'extensibleString123' should be valid",
                                 "extensibleString123", AvdManagerCli.validateResponse("extensibleString123", aProperty, mLogger));
                    assertNull(
                            "String enum 'extensibleStringPlus' should be invalid",
                            AvdManagerCli.validateResponse(
                                    "extensibleStringPlus", aProperty, mLogger));
                    assertNull(
                            "String enum '...' should be invalid",
                            AvdManagerCli.validateResponse("...", aProperty, mLogger));
                    assertNull(
                            "String enum 'fixedString3' should be invalid",
                            AvdManagerCli.validateResponse("fixedString3", aProperty, mLogger));
                    break;
                case "diskSizePropName":
                    assertEquals("Disk size '50MB' should be valid",
                                 "50MB", AvdManagerCli.validateResponse("50MB", aProperty, mLogger));
                    break;
                default:
                    fail("Unexpected hardware property type: " + aProperty.getName());
            }
        }
    }

    @Test
    public void packageHelp() {
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
    public void tagHelp() {
        try {
            mCli.run(
                    new String[] {
                        "create", "avd",
                        "--name", "testAvd",
                        "-k", "system-images;android-25;google_apis;x86",
                        "-d", "Nexus 6P",
                        "--tag", "foo"
                    });
            fail("Expected exception");
        } catch (Exception expected) {
            // expected
        }

        assertEquals(
                "E Invalid --tag foo for the selected package. Valid tags are:\n" + "google_apis",
                Joiner.on("").join(mLogger.getMessages()));
    }

    private void createHardwarePropertiesFile(String filePath) {
        mFileOp.recordExistingFile(filePath,
                                   "name        = booleanPropName\n"
                                   + "type        = boolean\n"
                                   + "default     = yes\n"
                                   + "abstract    = A bool\n"
                                   + "description = A bool value\n"

                                   + "name        = integerPropName\n"
                                   + "type        = integer\n"
                                   + "default     = 123\n"
                                   + "abstract    = An integer\n"
                                   + "description = A value that is integral\n"

                                   + "name        = integerEnumPropName\n"
                                   + "type        = integer\n"
                                   + "enum        = 10, 20, 30, 40\n"
                                   + "default     = 10\n"
                                   + "abstract    = An integer enum\n"
                                   + "description = One of a set of allowed integer values\n"

                                   + "name        = stringPropName\n"
                                   + "type        = string\n"
                                   + "default     = defString\n"
                                   + "abstract    = A string\n"
                                   + "description = A property that is a string\n"

                                   + "name        = stringEnumPropName\n"
                                   + "type        = string\n"
                                   + "enum        = okString0, okString1\n"
                                   + "default     = okString1\n"
                                   + "abstract    = A restricted string\n"
                                   + "description = One of a set of allowed values\n"

                                   + "name        = stringEnumTemplatePropName\n"
                                   + "type        = string\n"
                                   + "enum        = fixedString, anotherFixedString, extensibleString0, ...\n"
                                   + "default     = fixedString\n"
                                   + "abstract    = An extensible string\n"
                                   + "description = One of a set of extensible allowed values\n"

                                   + "name        = diskSizePropName\n"
                                   + "type        = diskSize\n"
                                   + "default     = 50MB\n"
                                   + "abstract    = A size with units\n"
                                   + "description = A string-like size with units\n");
    }
}
