package com.android.ide.common.rendering;

import static com.android.ide.common.rendering.HardwareConfigHelper.getGenericLabel;
import static com.android.ide.common.rendering.HardwareConfigHelper.getNexusLabel;
import static com.android.ide.common.rendering.HardwareConfigHelper.getNexusMenuLabel;
import static com.android.ide.common.rendering.HardwareConfigHelper.isGeneric;
import static com.android.ide.common.rendering.HardwareConfigHelper.isMobile;
import static com.android.ide.common.rendering.HardwareConfigHelper.isNexus;
import static com.android.ide.common.rendering.HardwareConfigHelper.isTv;
import static com.android.ide.common.rendering.HardwareConfigHelper.isWear;
import static com.android.ide.common.rendering.HardwareConfigHelper.nexusRank;
import static com.android.ide.common.rendering.HardwareConfigHelper.sortNexusListByRank;

import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.StdLogger;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import junit.framework.TestCase;

public class HardwareConfigHelperTest extends TestCase {
    private static DeviceManager getDeviceManager() {
        return DeviceManager.createInstance(
                new AndroidSdkHandler(null, null, new MockFileOp()), new StdLogger(StdLogger.Level.INFO));
    }

    public void testNexus() {
        DeviceManager deviceManager = getDeviceManager();
        Device n1 = deviceManager.getDevice("Nexus One", "Google");
        assertNotNull(n1);
        assertEquals("Nexus One", n1.getId());
        //noinspection deprecation
        assertSame(n1.getId(), n1.getName());
        assertEquals("Nexus One", n1.getDisplayName());
        assertTrue(isNexus(n1));

        assertEquals("Nexus One (3.7\", 480 \u00d7 800, hdpi)", getNexusLabel(n1));
        assertEquals("3.7, 480 \u00d7 800, hdpi (Nexus One)", getNexusMenuLabel(n1));
        assertFalse(isGeneric(n1));
        assertFalse(isGeneric(n1));
    }

    public void testNexus7() {
        DeviceManager deviceManager = getDeviceManager();
        Device n7 = deviceManager.getDevice("Nexus 7", "Google");
        Device n7b = deviceManager.getDevice("Nexus 7 2013", "Google");
        assertNotNull(n7);
        assertNotNull(n7b);
        assertEquals("Nexus 7 (2012)", n7.getDisplayName());
        assertEquals("Nexus 7", n7b.getDisplayName());

        assertEquals("Nexus 7 (2012) (7.0\", 800 \u00d7 1280, tvdpi)", getNexusLabel(n7));
        assertEquals("7.0, 800 \u00d7 1280, tvdpi (Nexus 7 2012)", getNexusMenuLabel(n7));

        assertTrue(isNexus(n7));
        assertTrue(isNexus(n7b));
        assertTrue(nexusRank(n7b) > nexusRank(n7));

        assertEquals("Nexus 7 (2012) (7.0\", 800 × 1280, tvdpi)", getNexusLabel(n7));
        assertEquals("Nexus 7 (7.0\", 1200 × 1920, xhdpi)", getNexusLabel(n7b));
        assertFalse(isGeneric(n7));
        assertFalse(isGeneric(n7));
    }

    public void testGeneric() {
        DeviceManager deviceManager = getDeviceManager();
        Device qvga = deviceManager.getDevice("2.7in QVGA", "Generic");
        assertNotNull(qvga);
        assertEquals("2.7\" QVGA", qvga.getDisplayName());
        assertEquals("2.7in QVGA", qvga.getId());
        assertFalse(isNexus(qvga));
        assertEquals(" 2.7\" QVGA (240 \u00d7 320, ldpi)", getGenericLabel(qvga));
        assertTrue(isGeneric(qvga));
        assertFalse(isNexus(qvga));
    }

    public void testIsWearIsTvIsRound() {
        DeviceManager deviceManager = getDeviceManager();
        Device qvga = deviceManager.getDevice("2.7in QVGA", "Generic");
        assertNotNull(qvga);
        assertFalse(isWear(qvga));
        assertFalse(isTv(qvga));
        assertTrue(isMobile(qvga));
        assertFalse(qvga.isScreenRound());

        Device nexus5 = deviceManager.getDevice("Nexus 5", "Google");
        assertNotNull(nexus5);
        assertFalse(isWear(nexus5));
        assertFalse(isTv(nexus5));
        assertTrue(isMobile(nexus5));
        assertFalse(nexus5.isScreenRound());

        Device square = deviceManager.getDevice("wear_square", "Google");
        assertNotNull(square);
        assertTrue(isWear(square));
        assertFalse(square.isScreenRound());
        assertFalse(isTv(square));
        assertFalse(isMobile(square));

        Device round = deviceManager.getDevice("wear_round", "Google");
        assertNotNull(round);
        assertTrue(isWear(round));
        assertTrue(round.isScreenRound());
        assertFalse(isTv(round));
        assertFalse(isMobile(round));
        assertEquals("Android Wear Round (1.7\", 320 \u00d7 320, hdpi)", getNexusLabel(round));
        assertEquals("320 \u00d7 320, hdpi (Round)", getNexusMenuLabel(round));


        Device tv1080p = deviceManager.getDevice("tv_1080p", "Google");
        assertNotNull(tv1080p);
        assertTrue(isTv(tv1080p));
        assertFalse(isWear(tv1080p));
        assertFalse(isMobile(tv1080p));
        assertFalse(tv1080p.isScreenRound());
        assertEquals("Android TV (1080p) (55.0\", 1920 \u00d7 1080, xhdpi)", getNexusLabel(tv1080p));
        assertEquals("1080p, 1920 \u00d7 1080, xhdpi (TV)", getNexusMenuLabel(tv1080p));

        Device tv720p = deviceManager.getDevice("tv_1080p", "Google");
        assertNotNull(tv720p);
        assertFalse(isWear(tv720p));
        assertTrue(isTv(tv720p));
        assertFalse(isMobile(tv720p));
        assertFalse(tv720p.isScreenRound());
    }

    public void testNexusRank() {
        List<Device> devices = Lists.newArrayList();
        DeviceManager deviceManager = getDeviceManager();
        for (String id : new String[] { "Nexus 7 2013", "Nexus 5", "Nexus 10", "Nexus 4", "Nexus 7",
                                        "Galaxy Nexus", "Nexus S", "Nexus One"}) {
            Device device = deviceManager.getDevice(id, "Google");
            assertNotNull(device);
            devices.add(device);
        }
        sortNexusListByRank(devices);
        Collections.reverse(devices);
        List<String> ids = Lists.newArrayList();
        for (Device device : devices) {
            ids.add(device.getId());
        }
        assertEquals(Arrays.asList(
                "Nexus One",
                "Nexus S",
                "Galaxy Nexus",
                "Nexus 7",
                "Nexus 10",
                "Nexus 4",
                "Nexus 7 2013",
                "Nexus 5"
        ), ids);
    }

    public void testScreenSize() {
        List<Device> devices = Lists.newArrayList();
        DeviceManager deviceManager = getDeviceManager();
        for (String id : new String[] { "Nexus 7 2013", "Nexus 5", "Nexus 10"}) {
            Device device = deviceManager.getDevice(id, "Google");
            assertNotNull(device);
            devices.add(device);
        }
        HardwareConfigHelper.sortDevicesByScreenSize(devices);
        List<String> ids = Lists.newArrayList();
        for (Device device : devices) {
            ids.add(device.getId());
        }
        assertEquals(Arrays.asList(
                "Nexus 5",
                "Nexus 7 2013",
                "Nexus 10"
        ), ids);
    }
}
