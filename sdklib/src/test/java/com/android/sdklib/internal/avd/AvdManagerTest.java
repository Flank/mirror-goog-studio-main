/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.sdklib.internal.avd;

import static com.google.common.truth.Truth.assertThat;

import com.android.io.CancellableFileIo;
import com.android.prefs.AbstractAndroidLocations;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.PathFileWrapper;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.testutils.MockLog;
import com.android.utils.NullLogger;
import com.google.common.collect.Maps;
import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import junit.framework.TestCase;

public class AvdManagerTest extends TestCase {

    private static final String ANDROID_PREFS_ROOT = "/android-home";

    private AndroidSdkHandler mAndroidSdkHandler;
    private AvdManager mAvdManager;
    private Path mAvdFolder;
    private SystemImage mSystemImageAosp;
    private SystemImage mSystemImageApi21;
    private SystemImage mSystemImageGoogle;
    private SystemImage mSystemImagePlay;
    private SystemImage mSystemImageWear24;
    private SystemImage mSystemImageWear25;
    private SystemImage mSystemImageWearChina;
    private SystemImage mSystemImageChromeOs;
    private final MockFileOp mFileOp = new MockFileOp();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mFileOp.recordExistingFile("/sdk/tools/lib/emulator/snapshots.img");
        recordGoogleApisSysImg23(mFileOp);
        recordPlayStoreSysImg24(mFileOp);
        recordSysImg21(mFileOp);
        recordSysImg23(mFileOp);
        recordWearSysImg24(mFileOp);
        recordWearSysImg25(mFileOp);
        recordWearSysImgChina(mFileOp);
        recordChromeOsSysImg(mFileOp);
        mAndroidSdkHandler =
                new AndroidSdkHandler(
                        mFileOp.toPath("/sdk"), mFileOp.toPath(ANDROID_PREFS_ROOT), mFileOp);
        mAvdManager =
                AvdManager.getInstance(
                        mAndroidSdkHandler,
                        new File(ANDROID_PREFS_ROOT, AbstractAndroidLocations.FOLDER_AVD)
                                .getAbsoluteFile(),
                        new NullLogger());
        mAvdFolder =
                mFileOp.toPath(AvdInfo.getDefaultAvdFolder(mAvdManager, getName(), mFileOp, false));

        for (SystemImage si : mAndroidSdkHandler.getSystemImageManager(new FakeProgressIndicator()).getImages()) {
            final String tagId = si.getTag().getId();
            if ("default".equals(tagId)) {
                if (si.getAndroidVersion().getApiLevel() == 21) {
                    mSystemImageApi21 = si;
                } else {
                    mSystemImageAosp = si;
                }
            } else if ("google_apis".equals(tagId)) {
                mSystemImageGoogle = si;
            } else if ("google_apis_playstore".equals(tagId)) {
                mSystemImagePlay = si;
            } else if ("android-wear".equals(tagId)) {
                if (si.getTag().getDisplay().contains("China")) {
                    mSystemImageWearChina = si;
                } else if (si.getAndroidVersion().getApiLevel() == 25) {
                    mSystemImageWear25 = si;
                } else {
                    mSystemImageWear24 = si;
                }
            } else if ("chromeos".equals(tagId)) {
                mSystemImageChromeOs = si;
            } else {
                fail("Created unexpected system image: " + tagId);
            }
        }
        assertNotNull(mSystemImageAosp);
        assertNotNull(mSystemImageApi21);
        assertNotNull(mSystemImageGoogle);
        assertNotNull(mSystemImagePlay);
        assertNotNull(mSystemImageWear24);
        assertNotNull(mSystemImageWear25);
        assertNotNull(mSystemImageWearChina);
        assertNotNull(mSystemImageChromeOs);
    }

    public void testCreateAvdWithoutSnapshot() {

        MockLog log = new MockLog();
        mAvdManager.createAvd(
                mAvdFolder,
                this.getName(),
                mSystemImageAosp,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                log);

        Path avdConfigFile = mAvdFolder.resolve("config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, CancellableFileIo.exists(avdConfigFile));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertFalse(CancellableFileIo.exists(mAvdFolder.resolve("boot.prop")));
        assertEquals(
                "system-images/android-23/default/x86/".replace('/', File.separatorChar),
                properties.get("image.sysdir.1"));
        assertNull(properties.get("snapshot.present"));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_IMG + " in " + mAvdFolder,
                CancellableFileIo.exists(mAvdFolder.resolve(AvdManager.USERDATA_IMG)));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                CancellableFileIo.exists(mAvdFolder.resolve(AvdManager.USERDATA_QEMU_IMG)));
        assertFalse(
                "Expected NO snapshots.img in " + mAvdFolder,
                CancellableFileIo.exists(mAvdFolder.resolve("snapshots.img")));
    }

    public void testCreateAvdWithUserdata() {

        MockLog log = new MockLog();
        mAvdManager.createAvd(
          mAvdFolder,
          this.getName(),
          mSystemImageApi21,
          null,
          null,
          null,
          null,
          null,
          false,
          false,
          false,
          log);

        Path avdConfigFile = mAvdFolder.resolve("config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, Files.exists(avdConfigFile));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertFalse(Files.exists(mAvdFolder.resolve("boot.prop")));
        assertEquals(
                "system-images/android-21/default/x86/".replace('/', File.separatorChar),
                properties.get("image.sysdir.1"));
        assertNull(properties.get("snapshot.present"));
        assertTrue(
                "Expected " + AvdManager.USERDATA_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_IMG)));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_QEMU_IMG)));
        assertFalse(
                "Expected NO snapshots.img in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve("snapshots.img")));
    }

    public void testCreateAvdWithBootProps() {
        MockLog log = new MockLog();
        Map<String, String> expected = Maps.newTreeMap();
        expected.put("ro.build.display.id", "sdk-eng 4.3 JB_MR2 774058 test-keys");
        expected.put("ro.board.platform",   "");
        expected.put("ro.build.tags",       "test-keys");

        mAvdManager.createAvd(
                mAvdFolder,
                this.getName(),
                mSystemImagePlay,
                null,
                null,
                null,
                null,
                expected,
                false,
                false,
                false,
                log);

        Path bootPropFile = mAvdFolder.resolve("boot.prop");
        assertTrue(Files.exists(bootPropFile));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(bootPropFile), null);

        // use a tree map to make sure test order is consistent
        assertEquals(expected.toString(), new TreeMap<>(properties).toString());
    }

    public void testCreateChromeOsAvd() {
        MockLog log = new MockLog();

        mAvdManager.createAvd(
                mAvdFolder,
                this.getName(),
                mSystemImageChromeOs,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                log);

        Path avdConfigFile = mAvdFolder.resolve("config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, Files.exists(avdConfigFile));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertEquals("true", properties.get("hw.arc"));
        assertEquals("x86_64", properties.get("hw.cpu.arch"));
    }

    public void testCreateNonChromeOsAvd() {
        MockLog log = new MockLog();

        mAvdManager.createAvd(
                mAvdFolder,
                this.getName(),
                mSystemImageAosp,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                log);

        Path avdConfigFile = mAvdFolder.resolve("config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, Files.exists(avdConfigFile));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertEquals("false", properties.get("hw.arc"));
        assertEquals("x86", properties.get("hw.cpu.arch"));
    }

    public void testRenameAvd() {

        MockLog log = new MockLog();
        // Create an AVD
        AvdInfo origAvd = mAvdManager.createAvd(
                mAvdFolder,
                this.getName(),
                mSystemImageAosp,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                log);

        assertNotNull("Could not create AVD", origAvd);
        Path avdConfigFile = mAvdFolder.resolve("config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, Files.exists(avdConfigFile));
        assertFalse(Files.exists(mAvdFolder.resolve("boot.prop")));
        Map<String, String> properties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertEquals(
                "system-images/android-23/default/x86/".replace('/', File.separatorChar),
                properties.get("image.sysdir.1"));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_IMG)));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_QEMU_IMG)));

        // Create an AVD that is the same, but with a different name
        String newName = this.getName() + "_renamed";
        AvdInfo renamedAvd = mAvdManager.createAvd(
                mAvdFolder,
                newName,
                mSystemImageAosp,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                true, // Yes, edit the existing AVD
                log);

        assertNotNull("Could not rename AVD", renamedAvd);
        Path parentFolder = mAvdFolder.getParent();
        String newNameIni = newName + ".ini";
        Path newAvdConfigFile = parentFolder.resolve(newNameIni);
        assertTrue(
                "Expected renamed " + newNameIni + " in " + parentFolder,
                Files.exists(newAvdConfigFile));
        Map<String, String> newProperties =
                AvdManager.parseIniFile(new PathFileWrapper(newAvdConfigFile), null);
        assertEquals(mAvdFolder.toString(), newProperties.get("path"));

        assertFalse(Files.exists(mAvdFolder.resolve("boot.prop")));
        avdConfigFile = mAvdFolder.resolve("config.ini");
        Map<String, String> baseProperties =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertEquals(
                "system-images/android-23/default/x86/".replace('/', File.separatorChar),
                baseProperties.get("image.sysdir.1"));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_IMG)));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_QEMU_IMG)));
    }

    public void testDuplicateAvd() throws Exception {

        MockLog log = new MockLog();
        // Create an AVD
        HashMap<String, String> origAvdConfig = new HashMap<>();
        origAvdConfig.put("testKey1", "originalValue1");
        origAvdConfig.put("testKey2", "originalValue2");
        AvdInfo origAvd = mAvdManager.createAvd(
          mAvdFolder,
          this.getName(),
          mSystemImagePlay,
          null,
          null,
          "100M", // SD card size
          origAvdConfig,
          null,
          false,
          false,
          false,
          log);

        assertNotNull("Could not create AVD", origAvd);
        // Put some extra files in the AVD directory
        Files.createFile(mAvdFolder.resolve("foo.bar"));
        mFileOp.recordExistingFile(
                mAvdFolder + "/hardware-qemu.ini",
                "avd.name="
                        + this.getName()
                        + "\nhw.sdCard.path="
                        + mAvdFolder.toAbsolutePath()
                        + "/sdcard.img");

        // Copy this AVDa to an AVD with a different name and a slightly different configuration
        HashMap<String, String> newAvdConfig = new HashMap<>();
        newAvdConfig.put("testKey2", "newValue2");

        String newName = "Copy_of_" + this.getName();
        AvdInfo duplicatedAvd = mAvdManager.createAvd(
          mAvdFolder,
          newName,
          mSystemImagePlay,
          null,
          null,
          "222M", // Different SD card size
          newAvdConfig,
          null,
          false,
          false,
          false, // Do not remove the original
          log);

        // Verify that the duplicated AVD is correct
        assertNotNull("Could not duplicate AVD", duplicatedAvd);
        Path parentFolder = mAvdFolder.getParent();
        String newFolderPath = parentFolder + File.separator + newName + ".avd";
        Path newFolder = mFileOp.toPath(newFolderPath);
        String newNameIni = newName + ".ini";
        Path newIniFile = parentFolder.resolve(newNameIni);
        assertTrue("Expected " + newNameIni + " in " + parentFolder, Files.exists(newIniFile));
        Map<String, String> iniProperties =
                AvdManager.parseIniFile(new PathFileWrapper(newIniFile), null);
        assertEquals(newFolder.toString(), iniProperties.get("path"));

        assertTrue(Files.exists(newFolder.resolve("foo.bar")));
        assertFalse(Files.exists(newFolder.resolve("boot.prop")));
        // Check the config.ini file
        Map<String, String> configProperties =
                AvdManager.parseIniFile(new PathFileWrapper(newFolder.resolve("config.ini")), null);
        assertEquals(
                "system-images/android-24/google_apis_playstore/x86_64/"
                        .replace('/', File.separatorChar),
                configProperties.get("image.sysdir.1"));
        assertEquals(newName, configProperties.get("AvdId"));
        assertEquals(newName, configProperties.get("avd.ini.displayname"));
        assertEquals("222M", configProperties.get("sdcard.size"));
        assertEquals("originalValue1", configProperties.get("testKey1"));
        assertEquals("newValue2", configProperties.get("testKey2"));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_IMG + " in " + newFolder,
                Files.exists(newFolder.resolve(AvdManager.USERDATA_IMG)));
        assertFalse(
                "Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                Files.exists(mAvdFolder.resolve(AvdManager.USERDATA_QEMU_IMG)));

        // Check the hardware-qemu.ini file
        Map<String, String> hardwareProperties =
                AvdManager.parseIniFile(
                        new PathFileWrapper(newFolder.resolve("hardware-qemu.ini")), null);
        assertEquals(newName, hardwareProperties.get("avd.name"));
        assertEquals(
                mAvdFolder.getParent().toAbsolutePath()
                        + File.separator
                        + newName
                        + ".avd/sdcard.img",
                hardwareProperties.get("hw.sdCard.path"));

        // Quick check that the original AVD directory still exists
        assertTrue(Files.exists(mAvdFolder.resolve("foo.bar")));
        assertTrue(Files.exists(mAvdFolder.resolve("config.ini")));
        assertTrue(Files.exists(mAvdFolder.resolve("hardware-qemu.ini")));
        Map<String, String> baseConfigProperties =
                AvdManager.parseIniFile(
                        new PathFileWrapper(mAvdFolder.resolve("config.ini")), null);
        assertThat(baseConfigProperties.get("AvdId")).isNotEqualTo(newName); // Different or null
    }

    public void testPlayStoreProperty() {
        MockLog log = new MockLog();
        Map<String, String> expected = Maps.newTreeMap();
        expected.put("ro.build.display.id", "sdk-eng 4.3 JB_MR2 774058 test-keys");
        expected.put("ro.board.platform",   "");
        expected.put("ro.build.tags",       "test-keys");

        // Play Store image with Play Store device
        mAvdManager.createAvd(
          mAvdFolder,
          this.getName(),
          mSystemImagePlay,
          null,
          null,
          null,
          null,
          expected,
          true, // deviceHasPlayStore
          false,
          false,
          log);
        Path configIniFile = mAvdFolder.resolve("config.ini");
        Map<String, String> baseProperties =
                AvdManager.parseIniFile(new PathFileWrapper(configIniFile), null);
        assertEquals("true", baseProperties.get("PlayStore.enabled"));

        // Play Store image with non-Play Store device
        mAvdManager.createAvd(
          mAvdFolder,
          this.getName(),
          mSystemImagePlay,
          null,
          null,
          null,
          null,
          expected,
          false, // deviceHasPlayStore
          true,
          false,
          log);
        baseProperties = AvdManager.parseIniFile(new PathFileWrapper(configIniFile), null);
        assertEquals("false", baseProperties.get("PlayStore.enabled"));

        // Non-Play Store image with Play Store device
        mAvdManager.createAvd(
          mAvdFolder,
          this.getName(),
          mSystemImageGoogle,
          null,
          null,
          null,
          null,
          expected,
          true, // deviceHasPlayStore
          true,
          false,
          log);
        baseProperties = AvdManager.parseIniFile(new PathFileWrapper(configIniFile), null);
        assertEquals("false", baseProperties.get("PlayStore.enabled"));

        // Wear image API 24 (no Play Store)
        mAvdManager.createAvd(
          mAvdFolder,
          this.getName(),
          mSystemImageWear24,
          null,
          null,
          null,
          null,
          expected,
          true, // deviceHasPlayStore
          true,
          false,
          log);
        baseProperties = AvdManager.parseIniFile(new PathFileWrapper(configIniFile), null);
        assertEquals("false", baseProperties.get("PlayStore.enabled"));

        // Wear image API 25 (with Play Store)
        // (All Wear devices have Play Store)
        mAvdManager.createAvd(
          mAvdFolder,
          this.getName(),
          mSystemImageWear25,
          null,
          null,
          null,
          null,
          expected,
          true, // deviceHasPlayStore
          true,
          false,
          log);
        baseProperties = AvdManager.parseIniFile(new PathFileWrapper(configIniFile), null);
        assertEquals("true", baseProperties.get("PlayStore.enabled"));

        // Wear image for China (no Play Store)
        mAvdManager.createAvd(
          mAvdFolder,
          this.getName(),
          mSystemImageWearChina,
          null,
          null,
          null,
          null,
          expected,
          true, // deviceHasPlayStore
          true,
          false,
          log);
        baseProperties = AvdManager.parseIniFile(new PathFileWrapper(configIniFile), null);
        assertEquals("false", baseProperties.get("PlayStore.enabled"));
    }

    public void testUpdateDeviceChanged() throws Exception {
        MockLog log = new MockLog();

        DeviceManager devMan = DeviceManager.createInstance(mAndroidSdkHandler, log);
        Device myDevice = devMan.getDevice("7.6in Foldable", "Generic");
        Map<String, String> baseHardwareProperties = DeviceManager.getHardwareProperties(myDevice);

        // Modify hardware properties that should change
        baseHardwareProperties.put("hw.lcd.height", "960");
        baseHardwareProperties.put("hw.displayRegion.0.1.height", "480");
        // Modify a hardware property that should NOT change
        baseHardwareProperties.put("hw.ramSize", "1536");
        // Add a user-settable property
        baseHardwareProperties.put("hw.keyboard", "yes");

        // Create a virtual device including these properties
        AvdInfo myDeviceInfo = mAvdManager.createAvd(
          mAvdFolder,
          this.getName(),
          mSystemImageGoogle,
          null,
          null,
          null,
          baseHardwareProperties,
          null,
          true,
          true,
          false,
          log);

        // Verify all the parameters that we changed and the parameter that we added
        Map<String, String> firstHardwareProperties = myDeviceInfo.getProperties();
        assertEquals("960",  firstHardwareProperties.get("hw.lcd.height"));
        assertEquals("480",  firstHardwareProperties.get("hw.displayRegion.0.1.height"));
        assertEquals("1536", firstHardwareProperties.get("hw.ramSize"));
        assertEquals("yes",  firstHardwareProperties.get("hw.keyboard"));

        // Update the device using the original hardware definition
        AvdInfo updatedDeviceInfo = mAvdManager.updateDeviceChanged(myDeviceInfo, log);

        // Verify that the two fixed hardware properties changed back, but the other hardware
        // property and the user-settable property did not change.
        Map<String, String> updatedHardwareProperties = updatedDeviceInfo.getProperties();
        assertEquals("2208", updatedHardwareProperties.get("hw.lcd.height"));
        assertEquals("2208",  updatedHardwareProperties.get("hw.displayRegion.0.1.height"));
        assertEquals("1536", updatedHardwareProperties.get("hw.ramSize"));
        assertEquals("yes",  updatedHardwareProperties.get("hw.keyboard"));
    }

    public void testParseAvdInfo() throws Exception {
        MockLog log = new MockLog();
        mAvdManager.createAvd(
          mAvdFolder,
          this.getName(),
          mSystemImageAosp,
          null,
          null,
          null,
          null,
          null,
          false,
          false,
          false,
          log);

        // Check a valid AVD .ini file
        Path parentFolder = mAvdFolder.getParent();
        String avdIniName = this.getName() + ".ini";
        Path avdIniFile = parentFolder.resolve(avdIniName).toAbsolutePath();
        assertTrue("Expected AVD .ini in " + parentFolder, Files.exists(avdIniFile));
        AvdInfo avdInfo = mAvdManager.parseAvdInfo(mFileOp.toFile(avdIniFile), log);
        assertThat(avdInfo.getStatus()).isEqualTo(AvdInfo.AvdStatus.OK);
        assertThat(avdInfo.getDataFolderPath()).isEqualTo(mAvdFolder.toAbsolutePath().toString());

        // Check a bad AVD .ini file.
        // Append garbage to make the file invalid.
        try (OutputStream corruptedStream =
                        Files.newOutputStream(avdIniFile, StandardOpenOption.APPEND);
                BufferedWriter corruptedWriter =
                        new BufferedWriter(new OutputStreamWriter(corruptedStream))) {
            corruptedWriter.write("[invalid syntax]\n");
        }
        AvdInfo corruptedInfo = mAvdManager.parseAvdInfo(mFileOp.toFile(avdIniFile), log);
        assertThat(corruptedInfo.getStatus()).isEqualTo(AvdInfo.AvdStatus.ERROR_CORRUPTED_INI);

        // Check a non-existent AVD .ini file
        String noSuchIniName = "noSuch.ini";
        Path noSuchIniFile = parentFolder.resolve(noSuchIniName);
        assertFalse("Found unexpected noSuch.ini in " + parentFolder, Files.exists(noSuchIniFile));
        AvdInfo noSuchInfo = mAvdManager.parseAvdInfo(mFileOp.toFile(noSuchIniFile), log);
        assertThat(noSuchInfo.getStatus()).isEqualTo(AvdInfo.AvdStatus.ERROR_CORRUPTED_INI);

        // Check an empty AVD .ini file
        Path emptyIniFile = parentFolder.resolve("empty.ini");
        assertNotNull(
                "Empty .ini file already exists in " + parentFolder,
                Files.createFile(emptyIniFile));
        assertTrue("Expected empty AVD .ini in " + parentFolder, Files.exists(emptyIniFile));
        assertThat(Files.size(emptyIniFile)).isEqualTo(0);
        AvdInfo emptyInfo = mAvdManager.parseAvdInfo(mFileOp.toFile(emptyIniFile), log);
        assertThat(emptyInfo.getStatus()).isEqualTo(AvdInfo.AvdStatus.ERROR_CORRUPTED_INI);
    }

    private static void recordSysImg21(MockFileOp fop) {
        fop.recordExistingFile("/sdk/system-images/android-21/default/x86/system.img");
        // Include userdata.img, but no data/ directory
        fop.recordExistingFile("/sdk/system-images/android-21/default/x86/"
                        + AvdManager.USERDATA_IMG);
        fop.recordExistingFile("/sdk/system-images/android-21/default/x86/skins/res1/layout");
        fop.recordExistingFile("/sdk/system-images/android-21/default/x86/skins/sample");
        fop.recordExistingFile("/sdk/system-images/android-21/default/x86/skins/res2/layout");
        fop.recordExistingFile("/sdk/system-images/android-21/default/x86/package.xml",
                               "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                               + "<ns3:sdk-sys-img "
                               + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                               + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                               + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                               + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                               + "<license id=\"license-A78C4257\" type=\"text\">Terms and Conditions\n"
                               + "</license><localPackage path=\"system-images;android-21;default;x86\" "
                               + "obsolete=\"false\">"
                               + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                               + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>21</api-level>"
                               + "<tag><id>default</id><display>Default</display></tag><abi>x86</abi>"
                               + "</type-details><revision><major>5</major></revision>"
                               + "<display-name>Intel x86 Atom System Image</display-name>"
                               + "<uses-license ref=\"license-A78C4257\"/></localPackage>"
                               + "</ns3:sdk-sys-img>\n");
    }

    private static void recordSysImg23(MockFileOp fop) {
        fop.recordExistingFile("/sdk/system-images/android-23/default/x86/system.img");
        // Include data/ directory, but no userdata.img file
        fop.mkdirs(new File("/sdk/system-images/android-23/default/x86/data"));
        fop.recordExistingFile("/sdk/system-images/android-23/default/x86/skins/res1/layout");
        fop.recordExistingFile("/sdk/system-images/android-23/default/x86/skins/sample");
        fop.recordExistingFile("/sdk/system-images/android-23/default/x86/skins/res2/layout");
        fop.recordExistingFile("/sdk/system-images/android-23/default/x86/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns3:sdk-sys-img "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-A78C4257\" type=\"text\">Terms and Conditions\n"
                        + "</license><localPackage path=\"system-images;android-23;default;x86\" "
                        + "obsolete=\"false\">"
                        + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>23</api-level>"
                        + "<tag><id>default</id><display>Default</display></tag><abi>x86</abi>"
                        + "</type-details><revision><major>5</major></revision>"
                        + "<display-name>Intel x86 Atom System Image</display-name>"
                        + "<uses-license ref=\"license-A78C4257\"/></localPackage>"
                        + "</ns3:sdk-sys-img>\n");
    }

    private static void recordGoogleApisSysImg23(MockFileOp fop) {
        fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/system.img");
        fop.mkdirs(new File("/sdk/system-images/android-23/google_apis/x86_64/data"));
        fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns3:sdk-sys-img "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-9A5C00D5\" type=\"text\">Terms and Conditions\n"
                        + "</license><localPackage "
                        + "path=\"system-images;android-23;google_apis;x86_64\" "
                        + "obsolete=\"false\"><type-details "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>23</api-level>"
                        + "<tag><id>google_apis</id><display>Google APIs</display></tag>"
                        + "<vendor><id>google</id><display>Google Inc.</display></vendor>"
                        + "<abi>x86_64</abi></type-details><revision><major>9</major></revision>"
                        + "<display-name>Google APIs Intel x86 Atom_64 System Image</display-name>"
                        + "<uses-license ref=\"license-9A5C00D5\"/></localPackage>"
                        + "</ns3:sdk-sys-img>\n");
    }

    private static void recordPlayStoreSysImg24(MockFileOp fop) {
        fop.recordExistingFile("/sdk/system-images/android-24/google_apis_playstore/x86_64/system.img");
        fop.mkdirs(new File("/sdk/system-images/android-24/google_apis_playstore/x86_64/data"));
        fop.recordExistingFile("/sdk/system-images/android-24/google_apis_playstore/x86_64/package.xml",
                               "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                               + "<ns3:sdk-sys-img "
                               + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                               + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                               + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                               + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                               + "<license id=\"license-9A5C00D5\" type=\"text\">Terms and Conditions\n"
                               + "</license><localPackage "
                               + "path=\"system-images;android-24;google_apis_playstore;x86_64\" "
                               + "obsolete=\"false\"><type-details "
                               + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                               + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>24</api-level>"
                               + "<tag><id>google_apis_playstore</id><display>Google Play</display></tag>"
                               + "<vendor><id>google</id><display>Google Inc.</display></vendor>"
                               + "<abi>x86_64</abi></type-details><revision><major>9</major></revision>"
                               + "<display-name>Google APIs with Playstore Intel x86 Atom System Image</display-name>"
                               + "<uses-license ref=\"license-9A5C00D5\"/></localPackage>"
                               + "</ns3:sdk-sys-img>\n");
    }

    private static void recordWearSysImg24(MockFileOp fop) {
        fop.recordExistingFile("/sdk/system-images/android-24/android-wear/x86/system.img");
        fop.recordExistingFile("/sdk/system-images/android-24/android-wear/x86/"
                               + AvdManager.USERDATA_IMG);
        fop.recordExistingFile("/sdk/system-images/android-24/android-wear/x86/package.xml",
                               "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                               + "<ns3:sdk-sys-img "
                               + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                               + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                               + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                               + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                               + "<license id=\"license-9A5C00D5\" type=\"text\">Terms and Conditions\n"
                               + "</license><localPackage "
                               + "path=\"system-images;android-24;android-wear;x86\" "
                               + "obsolete=\"false\"><type-details "
                               + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                               + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>24</api-level>"
                               + "<tag><id>android-wear</id><display>Android Wear</display></tag>"
                               + "<abi>x86</abi></type-details><revision><major>2</major></revision>"
                               + "<display-name>Android Wear Intel x86 Atom System Image</display-name>"
                               + "<uses-license ref=\"license-9A5C00D5\"/></localPackage>"
                               + "</ns3:sdk-sys-img>\n");
    }

    private static void recordWearSysImg25(MockFileOp fop) {
        fop.recordExistingFile("/sdk/system-images/android-25/android-wear/x86/system.img");
        fop.recordExistingFile("/sdk/system-images/android-25/android-wear/x86/"
                               + AvdManager.USERDATA_IMG);
        fop.recordExistingFile("/sdk/system-images/android-25/android-wear/x86/package.xml",
                               "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                               + "<ns3:sdk-sys-img "
                               + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                               + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                               + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                               + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                               + "<license id=\"license-9A5C00D5\" type=\"text\">Terms and Conditions\n"
                               + "</license><localPackage "
                               + "path=\"system-images;android-25;android-wear;x86\" "
                               + "obsolete=\"false\"><type-details "
                               + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                               + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>25</api-level>"
                               + "<tag><id>android-wear</id><display>Android Wear</display></tag>"
                               + "<abi>x86</abi></type-details><revision><major>2</major></revision>"
                               + "<display-name>Android Wear Intel x86 Atom System Image</display-name>"
                               + "<uses-license ref=\"license-9A5C00D5\"/></localPackage>"
                               + "</ns3:sdk-sys-img>\n");
    }

    private static void recordWearSysImgChina(MockFileOp fop) {
        fop.recordExistingFile("/sdk/system-images/android-25/android-wear-cn/x86/system.img");
        fop.recordExistingFile("/sdk/system-images/android-25/android-wear-cn/x86/"
                               + AvdManager.USERDATA_IMG);
        fop.recordExistingFile("/sdk/system-images/android-25/android-wear-cn/x86/package.xml",
                               "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                               + "<ns3:sdk-sys-img "
                               + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                               + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                               + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                               + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                               + "<license id=\"license-9A5C00D5\" type=\"text\">Terms and Conditions\n"
                               + "</license><localPackage "
                               + "path=\"system-images;android-25;android-wear-cn;x86\" "
                               + "obsolete=\"false\"><type-details "
                               + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                               + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>25</api-level>"
                               + "<tag><id>android-wear</id><display>Android Wear for China</display></tag>"
                               + "<abi>x86</abi></type-details><revision><major>2</major></revision>"
                               + "<display-name>Android Wear Intel x86 Atom System Image</display-name>"
                               + "<uses-license ref=\"license-9A5C00D5\"/></localPackage>"
                               + "</ns3:sdk-sys-img>\n");
    }

    private static void recordChromeOsSysImg(MockFileOp fop) {
        fop.recordExistingFile("/sdk/system-images/chromeos/m60/x86/system.img");
        fop.recordExistingFile("/sdk/system-images/chromeos/m60/x86/" + AvdManager.USERDATA_IMG);
        fop.recordExistingFile(
                "/sdk/system-images/chromeos/m60/x86/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns3:sdk-sys-img "
                        + "    xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\">"
                        + "  <localPackage path=\"system-images;chromeos;m60;x86\">"
                        + "    <type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "        xsi:type=\"ns3:sysImgDetailsType\"><api-level>25</api-level>"
                        + "      <tag>"
                        + "        <id>chromeos</id>"
                        + "        <display>Chrome OS</display>"
                        + "      </tag>"
                        + "      <abi>x86</abi>"
                        + "    </type-details>"
                        + "    <revision><major>1</major></revision>"
                        + "    <display-name>Chrome OS m60 System Image</display-name>"
                        + "  </localPackage>"
                        + "</ns3:sdk-sys-img>\n");
    }
}
