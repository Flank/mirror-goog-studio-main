/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.sdklib.devices;

import static com.google.common.truth.Truth.assertThat;

import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.resources.Keyboard;
import com.android.resources.Navigation;
import com.android.sdklib.TempSdkManager;
import com.android.sdklib.devices.Device.Builder;
import com.android.sdklib.devices.DeviceManager.DeviceFilter;
import com.android.sdklib.devices.DeviceManager.DeviceStatus;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImage;
import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.android.testutils.NoErrorsOrWarningsLogger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceManagerTest {

    @Rule public final TempSdkManager sdkManager =
            new TempSdkManager("sdk_" + getClass().getSimpleName());

    private DeviceManager dm;

    private NoErrorsOrWarningsLogger log;

    @Before
    public void setUp() {
        dm = createDeviceManager();
    }

    private DeviceManager createDeviceManager() {
        log = new NoErrorsOrWarningsLogger();
        AndroidSdkHandler sdkHandler = sdkManager.getSdkHandler();
        return DeviceManager.createInstance(
                sdkHandler,
                log);
    }

    /**
     * Returns a list of just the devices' display names, for unit test comparisons.
     */
    private static List<String> listDisplayNames(Collection<Device> devices) {
        return devices.stream().map(Device::getDisplayName).collect(Collectors.toList());
    }

    @Test
    public final void testGetDevices_Default() {
        // no user devices defined in the test's custom .android home folder
        assertThat(dm.getDevices(DeviceFilter.USER)).isEmpty();

        // no system-images devices defined in the SDK by default
        assertThat(dm.getDevices(DeviceFilter.SYSTEM_IMAGES)).isEmpty();

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.DEFAULT))).containsExactly(
                "10.1\" WXGA (Tablet)", "2.7\" QVGA", "2.7\" QVGA slider",
                "3.2\" HVGA slider (ADP1)", "3.2\" QVGA (ADP2)", "3.3\" WQVGA", "3.4\" WQVGA",
                "3.7\" FWVGA slider", "3.7\" WVGA (Nexus One)", "4\" WVGA (Nexus S)",
                "4.65\" 720p (Galaxy Nexus)", "4.7\" WXGA", "5.1\" WVGA", "5.4\" FWVGA",
                "7\" WSVGA (Tablet)");

        assertThat(dm.getDevice("2.7in QVGA", "Generic").getDisplayName()).isEqualTo("2.7\" QVGA");

        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.VENDOR))).containsExactly(
                "Android TV (1080p)", "Android TV (720p)", "Android Wear Round",
                "Android Wear Round Chin", "Android Wear Square", "Galaxy Nexus", "Nexus 10",
                "Nexus 4", "Nexus 5", "Nexus 5X", "Nexus 6", "Nexus 6P", "Nexus 7",
                "Nexus 7 (2012)", "Nexus 9", "Nexus One", "Nexus S", "Pixel C",
                "Pixel", "Pixel XL");

        assertThat(dm.getDevice("Nexus One", "Google").getDisplayName()).isEqualTo("Nexus One");

        assertThat(listDisplayNames(dm.getDevices(DeviceManager.ALL_DEVICES))).containsExactly(
                "10.1\" WXGA (Tablet)", "2.7\" QVGA", "2.7\" QVGA slider",
                "3.2\" HVGA slider (ADP1)", "3.2\" QVGA (ADP2)", "3.3\" WQVGA", "3.4\" WQVGA",
                "3.7\" FWVGA slider", "3.7\" WVGA (Nexus One)", "4\" WVGA (Nexus S)",
                "4.65\" 720p (Galaxy Nexus)", "4.7\" WXGA", "5.1\" WVGA", "5.4\" FWVGA",
                "7\" WSVGA (Tablet)", "Android TV (1080p)", "Android TV (720p)",
                "Android Wear Round", "Android Wear Round Chin", "Android Wear Square",
                "Galaxy Nexus", "Nexus 10", "Nexus 4", "Nexus 5", "Nexus 5X", "Nexus 6", "Nexus 6P",
                "Nexus 7", "Nexus 7 (2012)", "Nexus 9", "Nexus One", "Nexus S", "Pixel C",
                "Pixel", "Pixel XL");
    }

    @Test
    public final void testGetDevice() {
        // get a definition from the bundled devices.xml file
        Device d1 = dm.getDevice("7in WSVGA (Tablet)", "Generic");
        assertThat(d1.getDisplayName()).isEqualTo("7\" WSVGA (Tablet)");
        // get a definition from the bundled nexus.xml file
        Device d2 = dm.getDevice("Nexus One", "Google");
        assertThat(d2.getDisplayName()).isEqualTo("Nexus One");
    }

    @Test
    public final void testGetDevices_UserDevice() {

        Device d1 = dm.getDevice("7in WSVGA (Tablet)", "Generic");

        Builder b = new Device.Builder(d1);
        b.setId("MyCustomTablet");
        b.setName("My Custom Tablet");
        b.setManufacturer("OEM");

        Device d2 = b.build();

        dm.addUserDevice(d2);
        dm.saveUserDevices();

        assertThat(dm.getDevice("MyCustomTablet", "OEM").getDisplayName())
                .isEqualTo("My Custom Tablet");

        // create a new device manager, forcing it reload all files
        dm = null;
        DeviceManager dm2 = createDeviceManager();

        assertThat(dm2.getDevice("MyCustomTablet", "OEM").getDisplayName())
                .isEqualTo("My Custom Tablet");

        // 1 user device defined in the test's custom .android home folder
        assertThat(listDisplayNames(dm2.getDevices(DeviceFilter.USER)))
                .containsExactly("My Custom Tablet");

        // no system-images devices defined in the SDK by default
        assertThat(dm2.getDevices(DeviceFilter.SYSTEM_IMAGES)).isEmpty();

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertThat(listDisplayNames(dm2.getDevices(DeviceFilter.DEFAULT))).containsExactly(
                "10.1\" WXGA (Tablet)", "2.7\" QVGA", "2.7\" QVGA slider",
                "3.2\" HVGA slider (ADP1)", "3.2\" QVGA (ADP2)", "3.3\" WQVGA", "3.4\" WQVGA",
                "3.7\" FWVGA slider", "3.7\" WVGA (Nexus One)", "4\" WVGA (Nexus S)",
                "4.65\" 720p (Galaxy Nexus)", "4.7\" WXGA", "5.1\" WVGA", "5.4\" FWVGA",
                "7\" WSVGA (Tablet)");

        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertThat(listDisplayNames(dm2.getDevices(DeviceFilter.VENDOR))).containsExactly(
                "Android TV (1080p)", "Android TV (720p)", "Android Wear Round",
                "Android Wear Round Chin", "Android Wear Square", "Galaxy Nexus", "Nexus 10",
                "Nexus 4", "Nexus 5", "Nexus 5X", "Nexus 6", "Nexus 6P", "Nexus 7",
                "Nexus 7 (2012)", "Nexus 9", "Nexus One", "Nexus S", "Pixel C",
                "Pixel", "Pixel XL");

        assertThat(listDisplayNames(dm2.getDevices(DeviceManager.ALL_DEVICES))).containsExactly(
                "10.1\" WXGA (Tablet)", "2.7\" QVGA", "2.7\" QVGA slider",
                "3.2\" HVGA slider (ADP1)", "3.2\" QVGA (ADP2)", "3.3\" WQVGA", "3.4\" WQVGA",
                "3.7\" FWVGA slider", "3.7\" WVGA (Nexus One)", "4\" WVGA (Nexus S)",
                "4.65\" 720p (Galaxy Nexus)", "4.7\" WXGA", "5.1\" WVGA", "5.4\" FWVGA",
                "7\" WSVGA (Tablet)", "Android TV (1080p)", "Android TV (720p)",
                "Android Wear Round", "Android Wear Round Chin", "Android Wear Square",
                "Galaxy Nexus", "My Custom Tablet", "Nexus 10", "Nexus 4", "Nexus 5", "Nexus 5X",
                "Nexus 6", "Nexus 6P", "Nexus 7", "Nexus 7 (2012)", "Nexus 9", "Nexus One",
                "Nexus S", "Pixel C", "Pixel", "Pixel XL");
    }

    @Test
    public final void testGetDevices_SysImgDevice() throws Exception {

        File location = sdkManager.getSdkHandler().getLocation();
        FakePackage.FakeLocalPackage p = new FakePackage.FakeLocalPackage("dummy");

        // Create a system image directory with one device
        DetailsTypes.AddonDetailsType details = AndroidSdkHandler.getAddonModule()
                .createLatestFactory().createAddonDetailsType();
        details.setApiLevel(22);
        details.setVendor(SystemImage.DEFAULT_TAG);
        p.setTypeDetails((TypeDetails) details);
        SystemImage imageWithDevice = new SystemImage(
                new File(location, "system-images/android-22/tag-1/x86"),
                IdDisplay.create("tag-1", "tag-1"),
                IdDisplay.create("OEM", "Tag 1 OEM"),
                "x86", new File[]{}, p);

        sdkManager.makeSystemImageFolder(imageWithDevice, "tag-1");

        // no user devices defined in the test's custom .android home folder
        assertThat(dm.getDevices(DeviceFilter.USER)).isEmpty();

        // find the system-images specific device added by makeSystemImageFolder above
        // using both the getDevices() API and the device-specific getDevice() API.
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.SYSTEM_IMAGES)))
                .containsExactly("Mock Tag 1 Device Name");

        assertThat(dm.getDevice("tag-1", "OEM").getDisplayName())
                .isEqualTo("Mock Tag 1 Device Name");

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.DEFAULT))).containsExactly(
                "10.1\" WXGA (Tablet)", "2.7\" QVGA", "2.7\" QVGA slider",
                "3.2\" HVGA slider (ADP1)", "3.2\" QVGA (ADP2)", "3.3\" WQVGA", "3.4\" WQVGA",
                "3.7\" FWVGA slider", "3.7\" WVGA (Nexus One)", "4\" WVGA (Nexus S)",
                "4.65\" 720p (Galaxy Nexus)", "4.7\" WXGA", "5.1\" WVGA", "5.4\" FWVGA",
                "7\" WSVGA (Tablet)");

        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.VENDOR))).containsExactly(
                "Android TV (1080p)", "Android TV (720p)", "Android Wear Round",
                "Android Wear Round Chin", "Android Wear Square", "Galaxy Nexus", "Nexus 10",
                "Nexus 4", "Nexus 5", "Nexus 5X", "Nexus 6", "Nexus 6P", "Nexus 7",
                "Nexus 7 (2012)", "Nexus 9", "Nexus One", "Nexus S", "Pixel C",
                "Pixel", "Pixel XL");

        assertThat(listDisplayNames(dm.getDevices(DeviceManager.ALL_DEVICES))).containsExactly(
                "10.1\" WXGA (Tablet)", "2.7\" QVGA", "2.7\" QVGA slider",
                "3.2\" HVGA slider (ADP1)", "3.2\" QVGA (ADP2)", "3.3\" WQVGA", "3.4\" WQVGA",
                "3.7\" FWVGA slider", "3.7\" WVGA (Nexus One)", "4\" WVGA (Nexus S)",
                "4.65\" 720p (Galaxy Nexus)", "4.7\" WXGA", "5.1\" WVGA", "5.4\" FWVGA",
                "7\" WSVGA (Tablet)", "Android TV (1080p)", "Android TV (720p)",
                "Android Wear Round", "Android Wear Round Chin", "Android Wear Square",
                "Galaxy Nexus", "Mock Tag 1 Device Name", "Nexus 10", "Nexus 4", "Nexus 5",
                "Nexus 5X", "Nexus 6", "Nexus 6P", "Nexus 7", "Nexus 7 (2012)", "Nexus 9",
                "Nexus One", "Nexus S", "Pixel C", "Pixel", "Pixel XL");
    }

    @Test
    public final void testGetDeviceStatus() {
        // get a definition from the bundled devices.xml file
        assertThat(dm.getDeviceStatus("7in WSVGA (Tablet)", "Generic"))
                .isEqualTo(DeviceStatus.EXISTS);

        // get a definition from the bundled oem file
        assertThat(dm.getDeviceStatus("Nexus One", "Google")).isEqualTo(DeviceStatus.EXISTS);

        // try a device that does not exist
        assertThat(dm.getDeviceStatus("My Device", "Custom OEM")).isEqualTo(DeviceStatus.MISSING);
    }

    @Test
    public final void testGetHardwareProperties() {
        final Device pixelDevice = dm.getDevice("pixel", "Google");

        Map<String, String> devProperties = DeviceManager.getHardwareProperties(pixelDevice);
        assertThat(devProperties.get("hw.lcd.density")).isEqualTo("480");
        assertThat(devProperties.get("hw.lcd.width")).isEqualTo("1080");
    }

    @Test
    public final void testHasHardwarePropHashChanged_Generic() {
        final Device d1 = dm.getDevice("7in WSVGA (Tablet)", "Generic");

        assertThat(DeviceManager.hasHardwarePropHashChanged(d1, "invalid"))
                .isEqualTo("MD5:6f5876a1c548aef127b373f80cac4953");

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d1, "MD5:6f5876a1c548aef127b373f80cac4953"))
                .isNull();

        // change the device hardware props, this should change the hash
        d1.getDefaultHardware().setNav(Navigation.TRACKBALL);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d1, "MD5:6f5876a1c548aef127b373f80cac4953"))
                .isEqualTo("MD5:029c6388bae1062cfa3031d03edd36d8");

        // change the property back, should revert its hash to the previous one
        d1.getDefaultHardware().setNav(Navigation.NONAV);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d1, "MD5:6f5876a1c548aef127b373f80cac4953"))
                .isNull();
    }

    @Test
    public final void testHasHardwarePropHashChanged_Oem() {
        final Device d2 = dm.getDevice("Nexus One", "Google");

        assertThat(DeviceManager.hasHardwarePropHashChanged(d2, "invalid"))
                .isEqualTo("MD5:0250c2773d1dd25bb2b12d9502c789f7");

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d2, "MD5:0250c2773d1dd25bb2b12d9502c789f7"))
                .isNull();

        // change the device hardware props, this should change the hash
        d2.getDefaultHardware().setChargeType(PowerType.PLUGGEDIN);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d2, "MD5:0250c2773d1dd25bb2b12d9502c789f7"))
                .isEqualTo("MD5:efccdbbce8865090f04307054226afa9");

        // change the property back, should revert its hash to the previous one
        d2.getDefaultHardware().setChargeType(PowerType.BATTERY);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d2, "MD5:0250c2773d1dd25bb2b12d9502c789f7"))
                .isNull();
    }

    @Test
    public final void testDeviceOverrides() throws Exception {
        File location = sdkManager.getSdkHandler().getLocation();
        FakePackage.FakeLocalPackage p = new FakePackage.FakeLocalPackage("dummy");

        // Create a local DeviceManager, get the number of devices, and verify one device
        DeviceManager localDeviceManager = createDeviceManager();
        int count = localDeviceManager.getDevices(EnumSet.allOf(DeviceFilter.class)).size();
        Device localDevice = localDeviceManager.getDevice("wear_round", "Google");
        assertThat(localDevice.getDisplayName()).isEqualTo("Android Wear Round");

        // Create two system image directories with different definitions of the
        // device that we just checked. The version in android-25 should override
        // the other two versions.
        DetailsTypes.AddonDetailsType details22 = AndroidSdkHandler.getAddonModule()
                .createLatestFactory().createAddonDetailsType();
        details22.setApiLevel(22);
        details22.setVendor(SystemImage.DEFAULT_TAG);
        p.setTypeDetails((TypeDetails) details22);
        SystemImage imageWithDevice22 = new SystemImage(
                new File(location, "system-images/android-22/android-wear/x86"),
                IdDisplay.create("android-wear", "android-wear"),
                IdDisplay.create("Google", "Google"),
                "x86", new File[]{}, p);
        sdkManager.makeSystemImageFolder(imageWithDevice22, "wear_round");

        DetailsTypes.AddonDetailsType details25 = AndroidSdkHandler.getAddonModule()
          .createLatestFactory().createAddonDetailsType();
        details25.setApiLevel(25);
        details25.setVendor(SystemImage.DEFAULT_TAG);
        p.setTypeDetails((TypeDetails) details25);
        SystemImage imageWithDevice25 = new SystemImage(
                new File(location, "system-images/android-25/android-wear/x86"),
                IdDisplay.create("android-wear", "android-wear"),
                IdDisplay.create("Google", "Google"),
                "arm", new File[]{}, p);
        sdkManager.makeSystemImageFolder(imageWithDevice25, "wear_round");

        // Re-create the local DeviceManager using the new directory,
        // fetch the device, and verify that it is the right one.
        FakeProgressIndicator progress = new FakeProgressIndicator();
        sdkManager.getSdkHandler().getSdkManager(progress).markLocalCacheInvalid();
        localDeviceManager = createDeviceManager();

        localDevice = localDeviceManager.getDevice("wear_round", "Google");
        // (The "Android wear" part comes from the tag "android-wear")
        assertThat(localDevice.getDisplayName()).isEqualTo("Mock Android wear Device Name");
        assertThat(localDevice.getDefaultState().getHardware().getCpu()).isEqualTo("arm");

        // Change the name of that device and add it to our local DeviceManager again
        Device dmDevice = dm.getDevice("wear_round", "Google");
        Builder b = new Device.Builder(dmDevice);
        b.setName("Custom");
        localDeviceManager.addUserDevice(b.build());
        localDeviceManager.saveUserDevices();

        // Fetch the device from our local DeviceManager and verify
        // that it has the updated name
        localDevice = localDeviceManager.getDevice("wear_round", "Google");
        assertThat(localDevice.getDisplayName()).isEqualTo("Custom");

        // Verify that the total number of devices is unchanged
        assertThat(localDeviceManager.getDevices(EnumSet.allOf(DeviceFilter.class)).size()).isEqualTo(count);
    }

    @Test
    public final void testWriteUserDevice() throws Exception {
        Device testDeviceBefore = dm.getDevice("Test Round User Wear Device", "User");
        assertThat(testDeviceBefore).isNull();

        Device squareDevice = dm.getDevice("wear_square", "Google");
        String squareName = squareDevice.getDisplayName();
        assertThat(squareName).isEqualTo("Android Wear Square");
        assertThat(squareDevice.isScreenRound()).isFalse();

        Device.Builder devBuilder = new Device.Builder(squareDevice);
        devBuilder.setId("test_round_dev");
        devBuilder.setName("Test Round User Wear Device");
        devBuilder.setManufacturer("User");
        devBuilder.addBootProp(DeviceParser.ROUND_BOOT_PROP, "true");
        Device roundDevice = devBuilder.build();
        assertThat(roundDevice).isNotNull();

        dm.addUserDevice(roundDevice);

        Device testDeviceMid = dm.getDevice("test_round_dev", "User");
        assertThat(testDeviceMid).isNotNull();

        // Write the user-defined device definitions to devices.xml
        dm.saveUserDevices();
        dm.removeUserDevice(testDeviceMid);

        // Create a new DeviceManager. It will read the newly-written
        // devices.xml file, so we can check the contents.
        DeviceManager newDM = createDeviceManager();

        Device testDeviceAfter = newDM.getDevice("test_round_dev", "User");
        assertThat(testDeviceAfter).isNotNull();
        String afterName = testDeviceAfter.getDisplayName();
        assertThat(afterName).isEqualTo("Test Round User Wear Device");
        assertThat(testDeviceAfter.isScreenRound()).isTrue();
    }
}
