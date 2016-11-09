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

package com.android.sdklib.tool;

import com.android.prefs.AndroidLocation;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.testutils.MockLog;
import com.android.utils.NullLogger;
import com.google.common.collect.Maps;

import junit.framework.TestCase;

import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public class AvdManagerTest extends TestCase {

    private static final File ANDROID_HOME = new File("/android-home");

    private AndroidSdkHandler mAndroidSdkHandler;
    private AvdManager mAvdManager;
    private File mAvdFolder;
    private SystemImage mSystemImage;
    private MockFileOp mFileOp = new MockFileOp();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mFileOp.recordExistingFile("/sdk/tools/lib/emulator/snapshots.img");
        recordGoogleApisSysImg23(mFileOp);
        recordSysImg23(mFileOp);
        mAndroidSdkHandler =
                new AndroidSdkHandler(new File("/sdk"), ANDROID_HOME,  mFileOp);
        mAvdManager =
                AvdManager.getInstance(
                        mAndroidSdkHandler,
                        new File(ANDROID_HOME, AndroidLocation.FOLDER_AVD),
                        new NullLogger(),
                        mFileOp);
        mAvdFolder =
                AvdInfo.getDefaultAvdFolder(mAvdManager, getName(), mFileOp, false);
        mSystemImage = mAndroidSdkHandler.getSystemImageManager(
                new FakeProgressIndicator()).getImages().iterator().next();

    }

    public void testCreateAvdWithoutSnapshot() throws Exception {

        MockLog log = new MockLog();
        mAvdManager.createAvd(
                mAvdFolder,
                this.getName(),
                mSystemImage,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                log);

        assertTrue("Expected config.ini in " + mAvdFolder,
                mFileOp.exists(new File(mAvdFolder, "config.ini")));
        Properties properties = new Properties();
        properties.load(mFileOp.newFileInputStream(new File(mAvdFolder, "config.ini")));
        assertFalse(mFileOp.exists(new File(mAvdFolder, "boot.prop")));
        assertEquals("system-images/android-23/default/x86/", properties.get("image.sysdir.1"));
        assertEquals(null, properties.get("snapshot.present"));
        assertTrue("Expected userdata.img in " + mAvdFolder,
                mFileOp.exists(new File(mAvdFolder, "userdata.img")));
        assertFalse("Expected NO snapshots.img in " + mAvdFolder,
                mFileOp.exists(new File(mAvdFolder, "snapshots.img")));
    }

    public void testCreateAvdWithSnapshot() throws Exception {
        MockLog log = new MockLog();
        mAvdManager.createAvd(
                mAvdFolder,
                this.getName(),
                mSystemImage,
                null,
                null,
                null,
                null,
                null,
                true,
                false,
                false,
                log);

        assertTrue("Expected snapshots.img in " + mAvdFolder,
                mFileOp.exists(new File(mAvdFolder, "snapshots.img")));
        Properties properties = new Properties();
        properties.load(mFileOp.newFileInputStream(new File(mAvdFolder, "config.ini")));
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
                mSystemImage,
                null,
                null,
                null,
                null,
                expected,
                false,
                false,
                false,
                log);

        assertTrue(mFileOp.exists(new File(mAvdFolder, "boot.prop")));
        Properties properties = new Properties();
        properties.load(mFileOp.newFileInputStream(new File(mAvdFolder, "boot.prop")));

        // use a tree map to make sure test order is consistent
        assertEquals(expected.toString(), new TreeMap<>(properties).toString());
    }

    public void testRenameAvd() throws Exception {

        MockLog log = new MockLog();
        // Create an AVD
        AvdInfo origAvd = mAvdManager.createAvd(
                mAvdFolder,
                this.getName(),
                mSystemImage,
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
        assertTrue("Expected config.ini in " + mAvdFolder,
                mFileOp.exists(new File(mAvdFolder, "config.ini")));
        Properties properties = new Properties();
        properties.load(mFileOp.newFileInputStream(new File(mAvdFolder, "config.ini")));
        assertFalse(mFileOp.exists(new File(mAvdFolder, "boot.prop")));
        assertEquals("system-images/android-23/default/x86/", properties.get("image.sysdir.1"));
        assertTrue("Expected userdata.img in " + mAvdFolder,
                mFileOp.exists(new File(mAvdFolder, "userdata.img")));

        // Create an AVD that is the same, but with a different name
        String newName = this.getName() + "_renamed";
        AvdInfo renamedAvd = mAvdManager.createAvd(
                mAvdFolder,
                newName,
                mSystemImage,
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
        String parentFolder = mAvdFolder.getParent();
        String newNameIni = newName + ".ini";
        assertTrue("Expected renamed " + newNameIni + " in " + parentFolder,
                   mFileOp.exists(new File(parentFolder, newNameIni)));
        Properties newProperties = new Properties();
        newProperties.load(mFileOp.newFileInputStream(new File(parentFolder, newNameIni)));
        assertEquals(mAvdFolder.getPath(), newProperties.get("path"));

        assertFalse(mFileOp.exists(new File(mAvdFolder, "boot.prop")));
        Properties baseProperties = new Properties();
        baseProperties.load(mFileOp.newFileInputStream(new File(mAvdFolder, "config.ini")));
        assertEquals("system-images/android-23/default/x86/", baseProperties.get("image.sysdir.1"));
        assertTrue("Expected userdata.img in " + mAvdFolder,
                   mFileOp.exists(new File(mAvdFolder, "userdata.img")));
    }


    private static void recordSysImg23(MockFileOp fop) {
        fop.recordExistingFile("/sdk/system-images/android-23/default/x86/system.img");
        fop.recordExistingFile("/sdk/system-images/android-23/default/x86/userdata.img");
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
        fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/userdata.img");

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

}
