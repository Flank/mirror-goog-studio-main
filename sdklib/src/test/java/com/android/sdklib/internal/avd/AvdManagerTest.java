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

import com.android.prefs.AndroidLocation;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.FileOpFileWrapper;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.testutils.MockLog;
import com.android.utils.NullLogger;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.*;
import junit.framework.TestCase;

public class AvdManagerTest extends TestCase {

    private static final File ANDROID_HOME = new File("/android-home");

    private AndroidSdkHandler mAndroidSdkHandler;
    private AvdManager mAvdManager;
    private File mAvdFolder;
    private SystemImage mSystemImageAosp;
    private SystemImage mSystemImageGoogle;
    private SystemImage mSystemImagePlay;
    private SystemImage mSystemImageWear24;
    private SystemImage mSystemImageWear25;
    private SystemImage mSystemImageWearChina;
    private MockFileOp mFileOp = new MockFileOp();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mFileOp.recordExistingFile("/sdk/tools/lib/emulator/snapshots.img");
        recordGoogleApisSysImg23(mFileOp);
        recordPlayStoreSysImg24(mFileOp);
        recordSysImg23(mFileOp);
        recordWearSysImg24(mFileOp);
        recordWearSysImg25(mFileOp);
        recordWearSysImgChina(mFileOp);
        mAndroidSdkHandler =
                new AndroidSdkHandler(new File("/sdk"), ANDROID_HOME,  mFileOp);
        mAvdManager =
                AvdManager.getInstance(
                        mAndroidSdkHandler,
                        new File(ANDROID_HOME, AndroidLocation.FOLDER_AVD),
                        new NullLogger());
        mAvdFolder =
                AvdInfo.getDefaultAvdFolder(mAvdManager, getName(), mFileOp, false);

        for (SystemImage si : mAndroidSdkHandler.getSystemImageManager(new FakeProgressIndicator()).getImages()) {
            final String tagId = si.getTag().getId();
            if ("default".equals(tagId)) {
                mSystemImageAosp = si;
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
            } else {
                assertTrue("Created unexpected system image: " + tagId, false);
            }
        }
        assertNotNull(mSystemImageAosp);
        assertNotNull(mSystemImageGoogle);
        assertNotNull(mSystemImagePlay);
        assertNotNull(mSystemImageWear24);
        assertNotNull(mSystemImageWear25);
        assertNotNull(mSystemImageWearChina);
    }

    public void testCreateAvdWithoutSnapshot() throws Exception {

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
                false,
                log);

        File avdConfigFile = new File(mAvdFolder, "config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, mFileOp.exists(avdConfigFile));
        Map<String, String> properties = AvdManager.parseIniFile(
                new FileOpFileWrapper(avdConfigFile, mFileOp, false), null);
        assertFalse(mFileOp.exists(new File(mAvdFolder, "boot.prop")));
        assertEquals("system-images/android-23/default/x86/",
                mFileOp.getAgnosticAbsPath(properties.get("image.sysdir.1")));
        assertEquals(null, properties.get("snapshot.present"));
        assertTrue("Expected " + AvdManager.USERDATA_IMG + " in " + mAvdFolder,
                mFileOp.exists(new File(mAvdFolder, AvdManager.USERDATA_IMG)));
        assertFalse("Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                mFileOp.exists(new File(mAvdFolder, AvdManager.USERDATA_QEMU_IMG)));
        assertFalse("Expected NO snapshots.img in " + mAvdFolder,
                mFileOp.exists(new File(mAvdFolder, "snapshots.img")));
    }

    public void testCreateAvdWithSnapshot() throws Exception {
        MockLog log = new MockLog();
        mAvdManager.createAvd(
                mAvdFolder,
                this.getName(),
                mSystemImageGoogle,
                null,
                null,
                null,
                null,
                null,
                false,
                true,
                false,
                false,
                log);

        assertTrue("Expected snapshots.img in " + mAvdFolder,
                mFileOp.exists(new File(mAvdFolder, "snapshots.img")));
        Map<String, String> properties = AvdManager.parseIniFile(
                new FileOpFileWrapper(new File(mAvdFolder, "config.ini"), mFileOp, false), null);
        assertEquals("true", properties.get("snapshot.present"));
        assertFalse(mFileOp.exists(new File(mAvdFolder, "boot.prop")));
    }

    public void testCreateAvdWithBootProps() throws Exception {
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
                false,
                log);

        File bootPropFile = new File(mAvdFolder, "boot.prop");
        assertTrue(mFileOp.exists(bootPropFile));
        Map<String, String> properties = AvdManager.parseIniFile(
                new FileOpFileWrapper(bootPropFile, mFileOp, false), null);

        // use a tree map to make sure test order is consistent
        assertEquals(expected.toString(), new TreeMap<>(properties).toString());
    }

    public void testRenameAvd() throws Exception {

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
                false,
                log);

        assertNotNull("Could not create AVD", origAvd);
        File avdConfigFile = new File(mAvdFolder, "config.ini");
        assertTrue("Expected config.ini in " + mAvdFolder, mFileOp.exists(avdConfigFile));
        assertFalse(mFileOp.exists(new File(mAvdFolder, "boot.prop")));
        Map<String, String> properties = AvdManager.parseIniFile(
                new FileOpFileWrapper(avdConfigFile, mFileOp, false), null);
        assertEquals("system-images/android-23/default/x86/",
                mFileOp.getAgnosticAbsPath(properties.get("image.sysdir.1")));
        assertTrue("Expected " + AvdManager.USERDATA_IMG + " in " + mAvdFolder,
                mFileOp.exists(new File(mAvdFolder, AvdManager.USERDATA_IMG)));
        assertFalse("Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                mFileOp.exists(new File(mAvdFolder, AvdManager.USERDATA_QEMU_IMG)));

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
                false,
                true, // Yes, edit the existing AVD
                log);

        assertNotNull("Could not rename AVD", renamedAvd);
        String parentFolder = mAvdFolder.getParent();
        String newNameIni = newName + ".ini";
        File newAvdConfigFile = new File(parentFolder, newNameIni);
        assertTrue("Expected renamed " + newNameIni + " in " + parentFolder,
                mFileOp.exists(newAvdConfigFile));
        Map<String, String> newProperties = AvdManager.parseIniFile(
                new FileOpFileWrapper(newAvdConfigFile, mFileOp, false), null);
        assertEquals(mFileOp.getAgnosticAbsPath(mAvdFolder.getPath()),
                mFileOp.getAgnosticAbsPath(newProperties.get("path")));

        assertFalse(mFileOp.exists(new File(mAvdFolder, "boot.prop")));
        avdConfigFile = new File(mAvdFolder, "config.ini");
        Map<String, String> baseProperties = AvdManager.parseIniFile(
                new FileOpFileWrapper(avdConfigFile, mFileOp, false), null);
        assertEquals("system-images/android-23/default/x86/",
                mFileOp.getAgnosticAbsPath(baseProperties.get("image.sysdir.1")));
        assertTrue("Expected " + AvdManager.USERDATA_IMG + " in " + mAvdFolder,
                   mFileOp.exists(new File(mAvdFolder, AvdManager.USERDATA_IMG)));
        assertFalse("Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                   mFileOp.exists(new File(mAvdFolder, AvdManager.USERDATA_QEMU_IMG)));
    }

    public void testDuplicateAvd() throws Exception {

        MockLog log = new MockLog();
        // Create an AVD
        AvdInfo origAvd = mAvdManager.createAvd(
          mAvdFolder,
          this.getName(),
          mSystemImagePlay,
          null,
          null,
          null,
          null,
          null,
          false,
          false,
          false,
          false,
          log);

        assertNotNull("Could not create AVD", origAvd);
        // Put an extra file in the AVD directory
        mFileOp.createNewFile(new File(mAvdFolder, "foo.bar"));

        // Copy this AVD to an AVD with a different name
        String newName = "Copy_of_" + this.getName();
        AvdInfo duplicatedAvd = mAvdManager.createAvd(
          mAvdFolder,
          newName,
          mSystemImagePlay,
          null,
          null,
          null,
          null,
          null,
          false,
          false,
          false,
          false, // Do not remove the original
          log);

        // Verify that the duplicated AVD is correct
        assertNotNull("Could not duplicate AVD", duplicatedAvd);
        String parentFolder = mAvdFolder.getParent();
        String newFolder = parentFolder + "/" + newName + ".avd";
        String newNameIni = newName + ".ini";
        File newIniFile = new File(parentFolder, newNameIni);
        assertTrue("Expected " + newNameIni + " in " + parentFolder,
                mFileOp.exists(newIniFile));
        Map<String, String> iniProperties = AvdManager.parseIniFile(
                new FileOpFileWrapper(newIniFile, mFileOp, false), null);
        assertEquals(mFileOp.getAgnosticAbsPath(newFolder),
                mFileOp.getAgnosticAbsPath(iniProperties.get("path")));

        assertTrue(mFileOp.exists(new File(newFolder, "foo.bar")));
        assertFalse(mFileOp.exists(new File(newFolder, "boot.prop")));
        Map<String, String> configProperties = AvdManager.parseIniFile(new FileOpFileWrapper(
                new File(newFolder, "config.ini"), mFileOp, false), null);
        assertEquals(mFileOp.getAgnosticAbsPath(
                "system-images/android-24/google_apis_playstore/x86_64/"),
                mFileOp.getAgnosticAbsPath(configProperties.get("image.sysdir.1")));
        assertEquals(newName, configProperties.get("AvdId"));
        assertEquals(newName, configProperties.get("avd.ini.displayname"));
        assertTrue("Expected " + AvdManager.USERDATA_IMG + " in " + newFolder,
                   mFileOp.exists(new File(newFolder, AvdManager.USERDATA_IMG)));
        assertFalse("Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
                   mFileOp.exists(new File(mAvdFolder, AvdManager.USERDATA_QEMU_IMG)));
        // Quick check that the original AVD directory still exists
        assertTrue(mFileOp.exists(new File(mAvdFolder, "foo.bar")));
        assertTrue(mFileOp.exists(new File(mAvdFolder, "config.ini")));
        Map<String, String> baseConfigProperties = AvdManager.parseIniFile(
                new FileOpFileWrapper(new File(mAvdFolder, "config.ini"), mFileOp, false), null);
        assertThat(baseConfigProperties.get("AvdId")).isNotEqualTo(newName); // Different or null
    }

    public void testPlayStoreProperty() throws Exception {
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
          false,
          log);
        File configIniFile = new File(mAvdFolder, "config.ini");
        Map<String, String> baseProperties = AvdManager.parseIniFile(
                new FileOpFileWrapper(configIniFile, mFileOp, false), null);
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
          false,
          true,
          false,
          log);
        baseProperties = AvdManager.parseIniFile(
                new FileOpFileWrapper(configIniFile, mFileOp, false), null);
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
          false,
          true,
          false,
          log);
        baseProperties = AvdManager.parseIniFile(
                new FileOpFileWrapper(configIniFile, mFileOp, false), null);
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
          false,
          true,
          false,
          log);
        baseProperties = AvdManager.parseIniFile(
          new FileOpFileWrapper(configIniFile, mFileOp, false), null);
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
          false,
          true,
          false,
          log);
        baseProperties = AvdManager.parseIniFile(
          new FileOpFileWrapper(configIniFile, mFileOp, false), null);
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
          false,
          true,
          false,
          log);
        baseProperties = AvdManager.parseIniFile(
          new FileOpFileWrapper(configIniFile, mFileOp, false), null);
        assertEquals("false", baseProperties.get("PlayStore.enabled"));
    }


    private static void recordSysImg23(MockFileOp fop) {
        fop.recordExistingFile("/sdk/system-images/android-23/default/x86/system.img");
        fop.recordExistingFile("/sdk/system-images/android-23/default/x86/"
                        + AvdManager.USERDATA_IMG);
        fop.recordExistingFile("/sdk/system-images/android-23/default/x86/skins/res1/layout");
        fop.recordExistingFile("/sdk/system-images/android-23/default/x86/skins/dummy");
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
        fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/"
                        + AvdManager.USERDATA_IMG);

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
        fop.recordExistingFile("/sdk/system-images/android-24/google_apis_playstore/x86_64/"
                               + AvdManager.USERDATA_IMG);

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

}
