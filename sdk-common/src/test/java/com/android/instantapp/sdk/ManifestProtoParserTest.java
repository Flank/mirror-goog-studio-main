package com.android.instantapp.sdk;

import static com.android.instantapp.sdk.InstantAppSdkTests.getInstantAppSdk;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link ManifestProtoParser}. */
public class ManifestProtoParserTest {
    private ManifestProtoParser myManifestParser;
    private File myInstantAppSdk;

    @Before
    public void initializeParser() throws Exception {
        myInstantAppSdk = getInstantAppSdk();
        myManifestParser = new ManifestProtoParser(myInstantAppSdk);
        int a = 3;
    }

    @Test
    public void testEntireParse() throws Exception {
        Metadata metadata = myManifestParser.getMetadata();

        assertTrue(metadata.isSupportedArch("arm64-v8a"));
        assertTrue(metadata.isSupportedArch("armeabi-v7a"));
        assertTrue(metadata.isSupportedArch("x86"));
        assertFalse(metadata.isSupportedArch("mips"));

        assertTrue(
                metadata.isSupportedDevice(
                        new Metadata.Device("sony", null, Collections.singleton(23), null, null)));
        assertTrue(
                metadata.isSupportedDevice(
                        new Metadata.Device(
                                null, "bullhead", Collections.singleton(24), null, null)));
        assertTrue(
                metadata.isSupportedDevice(
                        new Metadata.Device(
                                null, null, Collections.singleton(25), null, "ranchu")));
        assertFalse(
                metadata.isSupportedDevice(
                        new Metadata.Device(
                                null, null, Collections.singleton(20), null, "ranchu")));
        assertFalse(
                metadata.isSupportedDevice(
                        new Metadata.Device(
                                "noManufacturer", null, Collections.singleton(25), null, null)));

        assertEquals(
                6,
                metadata.getGServicesOverrides(
                                new Metadata.Device(
                                        null, null, Collections.singleton(24), null, "ranchu"))
                        .size());
        assertEquals(
                4,
                metadata.getGServicesOverrides(
                                new Metadata.Device(
                                        "sony", null, Collections.singleton(23), null, null))
                        .size());

        assertEquals(4, metadata.getApks(Metadata.Arch.ARM64_V8A, 23).size());
        assertEquals(1, metadata.getApks(Metadata.Arch.DEFAULT, 23).size());

        for (Metadata.ApkInfo apkInfo : metadata.getApks(Metadata.Arch.X86, 22)) {
            if (apkInfo.getPkgName().compareTo("com.google.android.gms") == 0) {
                assertTrue(apkInfo.getApk().getName().contains("lmp"));
            }
        }
        for (Metadata.ApkInfo apkInfo : metadata.getApks(Metadata.Arch.X86, 24)) {
            if (apkInfo.getPkgName().compareTo("com.google.android.gms") == 0) {
                assertTrue(apkInfo.getApk().getName().contains("mnc"));
            }
        }

        assertEquals(4, metadata.getApks(Metadata.Arch.X86, 23).size());
        for (Metadata.ApkInfo apkInfo : metadata.getApks(Metadata.Arch.ARMEABI_V7A, 24)) {
            assertTrue(apkInfo.getApk().exists());
        }

        assertEquals(1, metadata.getAiaCompatApiMinVersion());

        assertEquals(23, metadata.getMinApiLevelSupported());
    }
}
