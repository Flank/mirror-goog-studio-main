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

import com.android.repository.Revision;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.resources.Keyboard;
import com.android.resources.Navigation;
import com.android.sdklib.TempAndroidLocation;
import com.android.sdklib.TempSdkManager;
import com.android.sdklib.devices.Device.Builder;
import com.android.sdklib.devices.DeviceManager.DeviceFilter;
import com.android.sdklib.devices.DeviceManager.DeviceStatus;
import com.android.testutils.MockLog;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class DeviceManagerTest {

    @Rule public final TempAndroidLocation androidLocation =
            new TempAndroidLocation("androidhome_" + getClass().getSimpleName());
    @Rule public final TempSdkManager sdkManager =
            new TempSdkManager("sdk_" + getClass().getSimpleName());

    private DeviceManager dm;

    private MockLog log;

    @Before
    public void setUp() {
        dm = createDeviceManager();
    }

    private DeviceManager createDeviceManager() {
        log = sdkManager.getLog();
        File sdkLocation = sdkManager.getSdkHandler().getLocation();
        return DeviceManager.createInstance(sdkLocation, log);
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
        assertThat(log.getMessages()).isEmpty();

        // no system-images devices defined in the SDK by default
        assertThat(dm.getDevices(DeviceFilter.SYSTEM_IMAGES)).isEmpty();
        assertThat(log.getMessages()).isEmpty();

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.DEFAULT))).containsExactly(
                "10.1\" WXGA (Tablet)", "2.7\" QVGA", "2.7\" QVGA slider",
                "3.2\" HVGA slider (ADP1)", "3.2\" QVGA (ADP2)", "3.3\" WQVGA", "3.4\" WQVGA",
                "3.7\" FWVGA slider", "3.7\" WVGA (Nexus One)", "4\" WVGA (Nexus S)",
                "4.65\" 720p (Galaxy Nexus)", "4.7\" WXGA", "5.1\" WVGA", "5.4\" FWVGA",
                "7\" WSVGA (Tablet)");
        assertThat(log.getMessages()).isEmpty();

        assertThat(dm.getDevice("2.7in QVGA", "Generic").getDisplayName()).isEqualTo("2.7\" QVGA");

        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.VENDOR))).containsExactly(
                "Android TV (1080p)", "Android TV (720p)", "Android Wear Round",
                "Android Wear Round Chin", "Android Wear Square", "Galaxy Nexus", "Nexus 10",
                "Nexus 4", "Nexus 5", "Nexus 5X", "Nexus 6", "Nexus 6P", "Nexus 7",
                "Nexus 7 (2012)", "Nexus 9", "Nexus One", "Nexus S", "Pixel C");
        assertThat(log.getMessages()).isEmpty();

        assertThat(dm.getDevice("Nexus One", "Google").getDisplayName()).isEqualTo("Nexus One");

        assertThat(listDisplayNames(dm.getDevices(DeviceManager.ALL_DEVICES))).containsExactly(
                "10.1\" WXGA (Tablet)", "2.7\" QVGA", "2.7\" QVGA slider",
                "3.2\" HVGA slider (ADP1)", "3.2\" QVGA (ADP2)", "3.3\" WQVGA", "3.4\" WQVGA",
                "3.7\" FWVGA slider", "3.7\" WVGA (Nexus One)", "4\" WVGA (Nexus S)",
                "4.65\" 720p (Galaxy Nexus)", "4.7\" WXGA", "5.1\" WVGA", "5.4\" FWVGA",
                "7\" WSVGA (Tablet)", "Android TV (1080p)", "Android TV (720p)",
                "Android Wear Round", "Android Wear Round Chin", "Android Wear Square",
                "Galaxy Nexus", "Nexus 10", "Nexus 4", "Nexus 5", "Nexus 5X", "Nexus 6", "Nexus 6P",
                "Nexus 7", "Nexus 7 (2012)", "Nexus 9", "Nexus One", "Nexus S", "Pixel C");
        assertThat(log.getMessages()).isEmpty();
    }

    @Test
    public final void testGetDevice() {
        // get a definition from the bundled devices.xml file
        Device d1 = dm.getDevice("7in WSVGA (Tablet)", "Generic");
        assertThat(d1.getDisplayName()).isEqualTo("7\" WSVGA (Tablet)");
        assertThat(log.getMessages()).isEmpty();

        // get a definition from the bundled nexus.xml file
        Device d2 = dm.getDevice("Nexus One", "Google");
        assertThat(d2.getDisplayName()).isEqualTo("Nexus One");
        assertThat(log.getMessages()).isEmpty();
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
        assertThat(log.getMessages()).isEmpty();

        // create a new device manager, forcing it reload all files
        dm = null;
        DeviceManager dm2 = createDeviceManager();

        assertThat(dm2.getDevice("MyCustomTablet", "OEM").getDisplayName())
                .isEqualTo("My Custom Tablet");
        assertThat(log.getMessages()).isEmpty();

        // 1 user device defined in the test's custom .android home folder
        assertThat(listDisplayNames(dm2.getDevices(DeviceFilter.USER)))
                .containsExactly("My Custom Tablet");
        assertThat(log.getMessages()).isEmpty();

        // no system-images devices defined in the SDK by default
        assertThat(dm2.getDevices(DeviceFilter.SYSTEM_IMAGES)).isEmpty();
        assertThat(log.getMessages()).isEmpty();

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertThat(listDisplayNames(dm2.getDevices(DeviceFilter.DEFAULT))).containsExactly(
                "10.1\" WXGA (Tablet)", "2.7\" QVGA", "2.7\" QVGA slider",
                "3.2\" HVGA slider (ADP1)", "3.2\" QVGA (ADP2)", "3.3\" WQVGA", "3.4\" WQVGA",
                "3.7\" FWVGA slider", "3.7\" WVGA (Nexus One)", "4\" WVGA (Nexus S)",
                "4.65\" 720p (Galaxy Nexus)", "4.7\" WXGA", "5.1\" WVGA", "5.4\" FWVGA",
                "7\" WSVGA (Tablet)");
        assertThat(log.getMessages()).isEmpty();

        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertThat(listDisplayNames(dm2.getDevices(DeviceFilter.VENDOR))).containsExactly(
                "Android TV (1080p)", "Android TV (720p)", "Android Wear Round",
                "Android Wear Round Chin", "Android Wear Square", "Galaxy Nexus", "Nexus 10",
                "Nexus 4", "Nexus 5", "Nexus 5X", "Nexus 6", "Nexus 6P", "Nexus 7",
                "Nexus 7 (2012)", "Nexus 9", "Nexus One", "Nexus S", "Pixel C");
        assertThat(log.getMessages()).isEmpty();

        assertThat(listDisplayNames(dm2.getDevices(DeviceManager.ALL_DEVICES))).containsExactly(
                "10.1\" WXGA (Tablet)", "2.7\" QVGA", "2.7\" QVGA slider",
                "3.2\" HVGA slider (ADP1)", "3.2\" QVGA (ADP2)", "3.3\" WQVGA", "3.4\" WQVGA",
                "3.7\" FWVGA slider", "3.7\" WVGA (Nexus One)", "4\" WVGA (Nexus S)",
                "4.65\" 720p (Galaxy Nexus)", "4.7\" WXGA", "5.1\" WVGA", "5.4\" FWVGA",
                "7\" WSVGA (Tablet)", "Android TV (1080p)", "Android TV (720p)",
                "Android Wear Round", "Android Wear Round Chin", "Android Wear Square",
                "Galaxy Nexus", "My Custom Tablet", "Nexus 10", "Nexus 4", "Nexus 5", "Nexus 5X",
                "Nexus 6", "Nexus 6P", "Nexus 7", "Nexus 7 (2012)", "Nexus 9", "Nexus One",
                "Nexus S", "Pixel C");
        assertThat(log.getMessages()).isEmpty();
    }

    @Test
    public final void testGetDevices_SysImgDevice() throws Exception {
        // this adds a devices.xml with one device
        sdkManager.makeSystemImageFolder("tag-1", "x86");

        // no user devices defined in the test's custom .android home folder
        assertThat(dm.getDevices(DeviceFilter.USER)).isEmpty();
        assertThat(log.getMessages()).isEmpty();

        // find the system-images specific device added by makeSystemImageFolder above
        // using both the getDevices() API and the device-specific getDevice() API.
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.SYSTEM_IMAGES)))
                .containsExactly("Mock Tag 1 Device Name");
        assertThat(log.getMessages()).isEmpty();

        assertThat(dm.getDevice("MockDevice-tag-1", "Mock Tag 1 OEM").getDisplayName())
                .isEqualTo("Mock Tag 1 Device Name");

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.DEFAULT))).containsExactly(
                "10.1\" WXGA (Tablet)", "2.7\" QVGA", "2.7\" QVGA slider",
                "3.2\" HVGA slider (ADP1)", "3.2\" QVGA (ADP2)", "3.3\" WQVGA", "3.4\" WQVGA",
                "3.7\" FWVGA slider", "3.7\" WVGA (Nexus One)", "4\" WVGA (Nexus S)",
                "4.65\" 720p (Galaxy Nexus)", "4.7\" WXGA", "5.1\" WVGA", "5.4\" FWVGA",
                "7\" WSVGA (Tablet)");
        assertThat(log.getMessages()).isEmpty();

        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.VENDOR))).containsExactly(
                "Android TV (1080p)", "Android TV (720p)", "Android Wear Round",
                "Android Wear Round Chin", "Android Wear Square", "Galaxy Nexus", "Nexus 10",
                "Nexus 4", "Nexus 5", "Nexus 5X", "Nexus 6", "Nexus 6P", "Nexus 7",
                "Nexus 7 (2012)", "Nexus 9", "Nexus One", "Nexus S", "Pixel C");
        assertThat(log.getMessages()).isEmpty();

        assertThat(listDisplayNames(dm.getDevices(DeviceManager.ALL_DEVICES))).containsExactly(
                "10.1\" WXGA (Tablet)", "2.7\" QVGA", "2.7\" QVGA slider",
                "3.2\" HVGA slider (ADP1)", "3.2\" QVGA (ADP2)", "3.3\" WQVGA", "3.4\" WQVGA",
                "3.7\" FWVGA slider", "3.7\" WVGA (Nexus One)", "4\" WVGA (Nexus S)",
                "4.65\" 720p (Galaxy Nexus)", "4.7\" WXGA", "5.1\" WVGA", "5.4\" FWVGA",
                "7\" WSVGA (Tablet)", "Android TV (1080p)", "Android TV (720p)",
                "Android Wear Round", "Android Wear Round Chin", "Android Wear Square",
                "Galaxy Nexus", "Mock Tag 1 Device Name", "Nexus 10", "Nexus 4", "Nexus 5",
                "Nexus 5X", "Nexus 6", "Nexus 6P", "Nexus 7", "Nexus 7 (2012)", "Nexus 9",
                "Nexus One", "Nexus S", "Pixel C");
        assertThat(log.getMessages()).isEmpty();
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
    public final void testHasHardwarePropHashChanged_Generic() {
        final Device d1 = dm.getDevice("7in WSVGA (Tablet)", "Generic");

        assertThat(DeviceManager.hasHardwarePropHashChanged(d1, "invalid"))
                .isEqualTo("MD5:750a657019b49e621c42ce9a20c2cc30");

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d1, "MD5:750a657019b49e621c42ce9a20c2cc30"))
                .isNull();

        // change the device hardware props, this should change the hash
        d1.getDefaultHardware().setNav(Navigation.TRACKBALL);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d1, "MD5:750a657019b49e621c42ce9a20c2cc30"))
                .isEqualTo("MD5:9c4dd5018987da51f7166f139f4361a2");

        // change the property back, should revert its hash to the previous one
        d1.getDefaultHardware().setNav(Navigation.NONAV);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d1, "MD5:750a657019b49e621c42ce9a20c2cc30"))
                .isNull();
    }

    @Test
    public final void testHasHardwarePropHashChanged_Oem() {
        final Device d2 = dm.getDevice("Nexus One", "Google");

        assertThat(DeviceManager.hasHardwarePropHashChanged(d2, "invalid"))
                .isEqualTo("MD5:36362a51e6c830c2ab515a312c9ecbff");

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d2, "MD5:36362a51e6c830c2ab515a312c9ecbff"))
                .isNull();

        // change the device hardware props, this should change the hash
        d2.getDefaultHardware().setKeyboard(Keyboard.QWERTY);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d2, "MD5:36362a51e6c830c2ab515a312c9ecbff"))
                .isEqualTo("MD5:f8f4b390755f2f58dfeb7d3020cd87db");

        // change the property back, should revert its hash to the previous one
        d2.getDefaultHardware().setKeyboard(Keyboard.NOKEY);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d2, "MD5:36362a51e6c830c2ab515a312c9ecbff"))
                .isNull();
    }

    @Test
    public final void testDeviceOverrides() throws Exception {
        File location = sdkManager.getSdkHandler().getLocation();
        FakePackage p = new FakePackage("dummy");
        DetailsTypes.AddonDetailsType details = AndroidSdkHandler.getAddonModule()
                .createLatestFactory().createAddonDetailsType();
        details.setApiLevel(22);
        details.setVendor(SystemImage.DEFAULT_TAG);
        p.setTypeDetails((TypeDetails) details);
        SystemImage imageWithDevice = new SystemImage(
                new File(location, "system-images/android-22/android-wear/x86"),
                IdDisplay.create("android-wear", "android-wear"),
                IdDisplay.create("Google", "Google1"),
                "x86", new File[]{}, p);
        DeviceManager manager = DeviceManager.createInstance(location, log);
        int count = manager.getDevices(EnumSet.allOf(DeviceFilter.class)).size();
        Device d = manager.getDevice("wear_round", "Google");
        assertThat(d.getDisplayName()).isEqualTo("Android Wear Round");

        sdkManager.makeSystemImageFolder(imageWithDevice, "wear_round");
        manager = DeviceManager.createInstance(location, log);

        d = manager.getDevice("wear_round", "Google");
        assertThat(d.getDisplayName()).isEqualTo("Mock Android wear Device Name");

        Device d1 = dm.getDevice("wear_round", "Google");

        Builder b = new Device.Builder(d1);
        b.setName("Custom");

        Device d2 = b.build();

        manager.addUserDevice(d2);
        manager.saveUserDevices();

        d = manager.getDevice("wear_round", "Google");
        assertThat(d.getDisplayName()).isEqualTo("Custom");
        assertThat(manager.getDevices(EnumSet.allOf(DeviceFilter.class)).size()).isEqualTo(count);
    }
}
