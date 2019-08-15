/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.sdklib;

import static org.junit.Assert.assertTrue;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.io.FileOpUtils;
import com.android.resources.Density;
import com.android.resources.Keyboard;
import com.android.resources.KeyboardState;
import com.android.resources.Navigation;
import com.android.resources.NavigationState;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenSize;
import com.android.resources.TouchScreen;
import com.android.sdklib.devices.ButtonType;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Device.Builder;
import com.android.sdklib.devices.DeviceWriter;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Multitouch;
import com.android.sdklib.devices.PowerType;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.ScreenType;
import com.android.sdklib.devices.Software;
import com.android.sdklib.devices.State;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.devices.Storage.Unit;
import com.android.sdklib.repository.legacy.local.LocalSdk;
import com.android.testutils.MockLog;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.legacy.local.LocalPlatformPkgInfo;
import com.android.sdklib.repository.legacy.local.LocalSysImgPkgInfo;

import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link org.junit.rules.TestRule} that allocates a temporary SDK, a temporary AVD base folder
 * with an SdkManager and an AvdManager that points to them.
 */
public class TempSdkManager extends ExternalResource {

    private static final String TARGET_DIR_NAME_0 = "android-0";
    private final String mTestName;

    private File mFakeSdk;
    private File mFakeAndroidFolder;

    private MockLog mLog;

    private AndroidSdkHandler mSdkHandler;

    public TempSdkManager(String testName) {
        mTestName = testName;
    }

    /**
     * Returns the {@link MockLog} for this test case.
     */
    public MockLog getLog() {
        return mLog;
    }

    public AndroidSdkHandler getSdkHandler() {
        return mSdkHandler;
    }

    /**
     * Sets up a {@link MockLog}, a fake SDK in a temporary directory and an AVD Manager pointing to
     * an initially-empty AVD directory.
     */
    @Override
    protected void before() throws Throwable {
        mLog = new MockLog();
        makeFakeSdk();
        makeFakeAndroidFolder();
        createSdkAvdManagers();
    }

    /**
     * Recreate the SDK and AVD Managers from scratch even if they already existed. Useful for tests
     * that want to reset their state without recreating the android-home or the fake SDK. The SDK
     * will be reparsed.
     */
    private void createSdkAvdManagers() {
        mSdkHandler = new AndroidSdkHandler(mFakeSdk, mFakeAndroidFolder, FileOpUtils.create());
    }

    /**
     * Removes the temporary SDK and AVD directories.
     */
    @Override
    protected void after() {
        tearDownSdk();
    }

    /**
     * Build enough of a skeleton SDK to make the tests pass. <p> Ideally this wouldn't touch the
     * file system but the current structure of the SdkManager and AvdManager makes this
     * impossible.
     */
    private void makeFakeSdk() throws IOException {
        // First we create a temp file to "reserve" the temp directory name we want to use.
        mFakeSdk = File.createTempFile(mTestName, "sdk");
        // Then erase the file and make the directory
        mFakeSdk.delete();
        mFakeSdk.mkdirs();

        File addonsDir = new File(mFakeSdk, SdkConstants.FD_ADDONS);
        addonsDir.mkdir();

        makePlatformTools(new File(mFakeSdk, SdkConstants.FD_PLATFORM_TOOLS));
        makeBuildTools(mFakeSdk);

        File platformsDir = new File(mFakeSdk, SdkConstants.FD_PLATFORMS);

        // Creating a fake target here on down
        File targetDir = makeFakeTargetInternal(platformsDir);
        makeFakeLegacySysImg(targetDir);

        makeFakeSkin(targetDir, "HVGA");
        makeFakeSourceInternal(mFakeSdk);
    }

    private void makeFakeAndroidFolder() throws IOException {
        mFakeAndroidFolder = File.createTempFile(mTestName, "android-home");
        mFakeAndroidFolder.delete();
        mFakeAndroidFolder.mkdirs();
    }

    private void tearDownSdk() {
        deleteDir(mFakeSdk);
    }

    /**
     * Creates the system image folder and places a fake userdata.img in it.
     *
     * @param systemImage A system image with a valid location.
     */
    public static void makeSystemImageFolder(ISystemImage systemImage, String deviceId)
            throws Exception {
        File sysImgDir = systemImage.getLocation();
        String vendor = systemImage.getAddonVendor() == null ? null
                : systemImage.getAddonVendor().getId();
        // Path should like SDK/system-images/platform-N/tag/abi/userdata.img+source.properties
        makeFakeSysImgInternal(
          sysImgDir,
          systemImage.getTag().getId(),
          systemImage.getAbiType(),
          deviceId,
          systemImage.getAndroidVersion().getApiString(),
          vendor);
    }

    /**
     * Creates the system image folder and places a fake userdata.img in it. This must be called
     * after {@link #before} so that it can use the temp fake SDK folder, and consequently you do
     * not need to specify the SDK root.
     *
     * @param tagId     An optional tag id. Use null for legacy no-tag system images.
     * @param abiType   The abi for the system image.
     * @return The directory of the system-image/tag/abi created.
     * @throws IOException if the file fails to be created.
     */
    @NonNull
    public File makeSystemImageFolder(
            @Nullable String tagId,
            @NonNull String abiType) throws Exception {
        File sysImgDir = new File(mFakeSdk, SdkConstants.FD_SYSTEM_IMAGES);
        sysImgDir = new File(sysImgDir, TARGET_DIR_NAME_0);
        if (tagId != null) {
            sysImgDir = new File(sysImgDir, tagId);
        }
        sysImgDir = new File(sysImgDir, abiType);

        makeFakeSysImgInternal(sysImgDir, tagId, abiType, null, null, null);
        return sysImgDir;
    }

    private static void createTextFile(File dir, String filepath, String... lines) throws IOException {
        File file = new File(dir, filepath);

        File parent = file.getParentFile();
        if (!parent.isDirectory()) {
            parent.mkdirs();
        }

        if (!file.isFile()) {
            assertTrue(file.createNewFile());
        }
        if (lines != null && lines.length > 0) {
            FileWriter out = new FileWriter(file);
            for (String line : lines) {
                out.write(line);
            }
            out.close();
        }
    }

    /**
     * Utility used by {@link #makeFakeSdk()} to create a fake target with API 0, rev 0.
     */
    private static File makeFakeTargetInternal(File platformsDir) throws IOException {
        File targetDir = new File(platformsDir, TARGET_DIR_NAME_0);
        targetDir.mkdirs();
        new File(targetDir, SdkConstants.FN_FRAMEWORK_LIBRARY).createNewFile();
        new File(targetDir, SdkConstants.FN_FRAMEWORK_AIDL).createNewFile();

        createSourceProps(targetDir,
                PkgProps.PKG_REVISION, "1",
                PkgProps.PLATFORM_VERSION, "0.0",
                PkgProps.VERSION_API_LEVEL, "0",
                PkgProps.LAYOUTLIB_API, "5",
                PkgProps.LAYOUTLIB_REV, "2");

        createFileProps(SdkConstants.FN_BUILD_PROP, targetDir,
                LocalPlatformPkgInfo.PROP_VERSION_RELEASE, "0.0",
                LocalPlatformPkgInfo.PROP_VERSION_SDK, "0",
                LocalPlatformPkgInfo.PROP_VERSION_CODENAME, "REL");

        return targetDir;
    }

    /**
     * Utility to create a fake *legacy* sys image in a platform folder. Legacy system images follow
     * that path pattern: $SDK/platforms/platform-N/images/userdata.img
     *
     * They have no source.properties file in that directory.
     */
    private static void makeFakeLegacySysImg(@NonNull File platformDir) throws IOException {
        File imagesDir = new File(platformDir, "images");
        imagesDir.mkdirs();
        new File(imagesDir, "userdata.img").createNewFile();
    }

    /**
     * Utility to create a fake sys image in the system-images folder.
     *
     * "modern" (as in "not legacy") system-images follow that path pattern:
     * $SDK/system-images/platform-N/abi/source.properties
     * $SDK/system-images/platform-N/abi/userdata.img
     * $SDK/system-images/platform-N/tag/abi/source.properties
     * $SDK/system-images/platform-N/tag/abi/userdata.img
     *
     * The tag id is optional and was only introduced in API 20 / Tools 22.6. The platform-N and the
     * tag folder names are irrelevant as the info from source.properties matters most.
     */
    private static void makeFakeSysImgInternal(
            @NonNull File sysImgDir,
            @Nullable String tagId,
            @NonNull String abiType,
            @Nullable String deviceId,
            @Nullable String apiLevel,
            @Nullable String deviceMfg) throws Exception {
        sysImgDir.mkdirs();
        new File(sysImgDir, "userdata.img").createNewFile();

        if (tagId == null) {
            createSourceProps(sysImgDir,
                    PkgProps.PKG_REVISION, "0",
                    PkgProps.VERSION_API_LEVEL, "0",
                    PkgProps.SYS_IMG_ABI, abiType);
        } else {
            String tagDisplay = LocalSysImgPkgInfo.tagIdToDisplay(tagId);
            createSourceProps(sysImgDir,
                    PkgProps.PKG_REVISION, "0",
                    PkgProps.VERSION_API_LEVEL, apiLevel,
                    PkgProps.SYS_IMG_TAG_ID, tagId,
                    PkgProps.SYS_IMG_TAG_DISPLAY, tagDisplay,
                    PkgProps.SYS_IMG_ABI, abiType,
                    PkgProps.PKG_LIST_DISPLAY,
                    "Sys-Img v0 for (" + tagDisplay + ", " + abiType + ")");

            // create a devices.xml file
            List<Device> devices = new ArrayList<>();
            Builder b = new Device.Builder();
            b.setName("Mock " + tagDisplay + " Device Name");
            b.setId(deviceId == null ? "MockDevice-" + tagId : deviceId);
            b.setManufacturer(deviceMfg == null ? "Mock " + tagDisplay + " OEM" : deviceMfg);

            Software sw = new Software();
            sw.setGlVersion("4.2");
            sw.setLiveWallpaperSupport(false);
            sw.setMaxSdkLevel(42);
            sw.setMinSdkLevel(1);
            sw.setStatusBar(true);

            Screen sc = new Screen();
            sc.setDiagonalLength(7);
            sc.setMechanism(TouchScreen.FINGER);
            sc.setMultitouch(Multitouch.JAZZ_HANDS);
            sc.setPixelDensity(Density.HIGH);
            sc.setRatio(ScreenRatio.NOTLONG);
            sc.setScreenType(ScreenType.CAPACITIVE);
            sc.setSize(ScreenSize.LARGE);
            sc.setXDimension(5);
            sc.setXdpi(100);
            sc.setYDimension(4);
            sc.setYdpi(100);

            Hardware hw = new Hardware();
            hw.setButtonType(ButtonType.SOFT);
            hw.setChargeType(PowerType.BATTERY);
            hw.setCpu(abiType);
            hw.setGpu("pixelpushing");
            hw.setHasMic(true);
            hw.setKeyboard(Keyboard.QWERTY);
            hw.setNav(Navigation.NONAV);
            hw.setRam(new Storage(512, Unit.MiB));
            hw.setScreen(sc);

            State st = new State();
            st.setName("portrait");
            st.setDescription("Portrait");
            st.setDefaultState(true);
            st.setOrientation(ScreenOrientation.PORTRAIT);
            st.setKeyState(KeyboardState.SOFT);
            st.setNavState(NavigationState.HIDDEN);
            st.setHardware(hw);

            b.addSoftware(sw);
            b.addState(st);

            devices.add(b.build());

            File f = new File(sysImgDir, "devices.xml");
            FileOutputStream fos = new FileOutputStream(f);
            DeviceWriter.writeToXml(fos, devices);
            fos.close();
        }
    }

    /**
     * Utility to make a fake skin for the given target
     */
    private static void makeFakeSkin(File targetDir, String skinName) throws IOException {
        File skinFolder = FileOpUtils.append(targetDir, "skins", skinName);
        skinFolder.mkdirs();

        // To be detected properly, the skin folder should have a "layout" file.
        // Its content is however not parsed.
        FileWriter out = new FileWriter(new File(skinFolder, "layout"));
        out.write("parts {\n}\n");
        out.close();
    }

    /**
     * Utility to create a fake source with a few files in the given sdk folder.
     */
    private static void makeFakeSourceInternal(File sdkDir) throws IOException {
        File sourcesDir = FileOpUtils.append(sdkDir, SdkConstants.FD_PKG_SOURCES, "android-0");
        sourcesDir.mkdirs();

        createSourceProps(sourcesDir, PkgProps.VERSION_API_LEVEL, "0");

        File dir1 = FileOpUtils.append(sourcesDir, "src", "com", "android");
        dir1.mkdirs();
        FileOpUtils.append(dir1, "File1.java").createNewFile();
        FileOpUtils.append(dir1, "File2.java").createNewFile();

        FileOpUtils.append(sourcesDir, "res", "values").mkdirs();
        FileOpUtils.append(sourcesDir, "res", "values", "styles.xml").createNewFile();
    }

    private static void makePlatformTools(File platformToolsDir) throws IOException {
        platformToolsDir.mkdir();
        createSourceProps(platformToolsDir, PkgProps.PKG_REVISION, "17.1.2");

        // platform-tools revision >= 17 requires only an adb file to be valid.
        new File(platformToolsDir, SdkConstants.FN_ADB).createNewFile();
    }

    private static void makeBuildTools(File sdkDir) throws IOException {
        for (String revision : new String[]{"3.0.0", "3.0.1", "18.3.4-rc5"}) {
            createFakeBuildTools(sdkDir, "ANY", revision);
        }
    }

    /**
     * Adds a new fake build tools to the SDK In the given SDK/build-tools folder.
     *
     * @param sdkDir   The SDK top folder. Must already exist.
     * @param os       The OS. One of HostOs#toString() or "ANY".
     * @param revisionStr The "x.y.z rc r" revisionStr number from {@link Revision#toShortString()}.
     */
    private static void createFakeBuildTools(File sdkDir, String os, String revisionStr)
            throws IOException {
        File buildToolsTopDir = new File(sdkDir, SdkConstants.FD_BUILD_TOOLS);
        buildToolsTopDir.mkdir();
        File buildToolsDir = new File(buildToolsTopDir, revisionStr);
        createSourceProps(buildToolsDir,
                PkgProps.PKG_REVISION, revisionStr,
                "Archive.Os", os);

        Revision revision = Revision.parseRevision(revisionStr);

        createFakeBuildToolsFile(
                buildToolsDir, revision,
                BuildToolInfo.PathId.AIDL, SdkConstants.FN_AIDL);
        createFakeBuildToolsFile(
                buildToolsDir, revision,
                BuildToolInfo.PathId.DX, SdkConstants.FN_DX);
        createFakeBuildToolsFile(
                buildToolsDir, revision,
                BuildToolInfo.PathId.DX_JAR, SdkConstants.FD_LIB + File.separator +
                        SdkConstants.FN_DX_JAR);
        createFakeBuildToolsFile(
                buildToolsDir, revision,
                BuildToolInfo.PathId.LLVM_RS_CC, SdkConstants.FN_RENDERSCRIPT);
        createFakeBuildToolsFile(
                buildToolsDir, revision,
                BuildToolInfo.PathId.ANDROID_RS, SdkConstants.OS_FRAMEWORK_RS + File.separator +
                        "placeholder.txt");
        createFakeBuildToolsFile(
                buildToolsDir, revision,
                BuildToolInfo.PathId.ANDROID_RS_CLANG,
                SdkConstants.OS_FRAMEWORK_RS_CLANG + File.separator +
                        "placeholder.txt");
        createFakeBuildToolsFile(
                buildToolsDir, revision,
                BuildToolInfo.PathId.BCC_COMPAT, SdkConstants.FN_BCC_COMPAT);
        createFakeBuildToolsFile(
                buildToolsDir, revision,
                BuildToolInfo.PathId.LD_ARM, SdkConstants.FN_LD_ARM);
        createFakeBuildToolsFile(
                buildToolsDir, revision,
                BuildToolInfo.PathId.LD_ARM64, SdkConstants.FN_LD_ARM64);
        createFakeBuildToolsFile(
                buildToolsDir, revision,
                BuildToolInfo.PathId.LD_MIPS, SdkConstants.FN_LD_MIPS);
        createFakeBuildToolsFile(
                buildToolsDir, revision,
                BuildToolInfo.PathId.LD_X86, SdkConstants.FN_LD_X86);
        createFakeBuildToolsFile(
                buildToolsDir, revision,
                BuildToolInfo.PathId.LD_X86_64, SdkConstants.FN_LD_X86_64);
    }

    private static void createFakeBuildToolsFile(@NonNull File dir,
            @NonNull Revision buildToolsRevision,
            @NonNull BuildToolInfo.PathId pathId,
            @NonNull String filepath)
            throws IOException {

        if (pathId.isPresentIn(buildToolsRevision)) {
            createTextFile(dir, filepath);
        }
    }

    private static void createSourceProps(File parentDir, String... paramValuePairs) throws IOException {
        createFileProps(SdkConstants.FN_SOURCE_PROP, parentDir, paramValuePairs);
    }

    private static void createFileProps(String fileName, File parentDir, String... paramValuePairs)
            throws IOException {
        File sourceProp = new File(parentDir, fileName);
        parentDir = sourceProp.getParentFile();
        if (!parentDir.isDirectory()) {
            assertTrue(parentDir.mkdirs());
        }
        if (!sourceProp.isFile()) {
            assertTrue(sourceProp.createNewFile());
        }
        FileWriter out = new FileWriter(sourceProp);
        int n = paramValuePairs.length;
        assertTrue("paramValuePairs must have an even length, format [param=value]+", n % 2 == 0);
        for (int i = 0; i < n; i += 2) {
            out.write(paramValuePairs[i] + '=' + paramValuePairs[i + 1] + '\n');
        }
        out.close();

    }

    /**
     * Recursive delete directory. Mostly for fake SDKs.
     *
     * @param root directory to delete
     */
    private static void deleteDir(File root) {
        if (root.exists()) {
            for (File file : root.listFiles()) {
                if (file.isDirectory()) {
                    deleteDir(file);
                } else {
                    file.delete();
                }
            }
            root.delete();
        }
    }
}
