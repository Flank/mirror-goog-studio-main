package com.android.instantapp.provision;

import static com.android.instantapp.sdk.InstantAppSdkTests.getInstantAppSdk;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.ddmlib.IDevice;
import com.android.instantapp.sdk.InstantAppSdkTests;
import com.android.instantapp.sdk.Metadata;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link ProvisionApksInstaller}. */
public class ProvisionApksInstallerTest {
    private ProvisionApksInstaller myApksInstaller;
    private File myInstantAppSdk;

    @Before
    public void setUp() throws Exception {
        myInstantAppSdk = getInstantAppSdk();
        List<Metadata.ApkInfo> apkInfos =
                Lists.newArrayList(
                        new Metadata.ApkInfo(
                                "com.google.android.instantapps.supervisor",
                                FileUtils.join(
                                        myInstantAppSdk,
                                        "tools",
                                        "apks",
                                        "release",
                                        "supervisor_x86.apk"),
                                Metadata.Arch.X86,
                                Sets.newHashSet(23, 24, 25),
                                10486),
                        new Metadata.ApkInfo(
                                "com.google.android.instantapps.devman",
                                FileUtils.join(
                                        myInstantAppSdk, "tools", "apks", "release", "devman.apk"),
                                Metadata.Arch.DEFAULT,
                                Sets.newHashSet(23, 24, 25),
                                37),
                        new Metadata.ApkInfo(
                                "com.google.android.gms",
                                FileUtils.join(
                                        myInstantAppSdk,
                                        "tools",
                                        "apks",
                                        "debug",
                                        "GmsCore_prodmnc_xxhdpi_x86.apk"),
                                Metadata.Arch.X86,
                                Sets.newHashSet(23, 24, 25),
                                11034440),
                        new Metadata.ApkInfo(
                                "com.google.android.gms",
                                FileUtils.join(
                                        myInstantAppSdk,
                                        "tools",
                                        "apks",
                                        "release",
                                        "GmsCore_prodmnc_xxhdpi_x86.apk"),
                                Metadata.Arch.X86,
                                Sets.newHashSet(23, 24, 25),
                                11034440));
        myApksInstaller = new ProvisionApksInstaller(apkInfos);
    }

    @Test
    public void testDevManFirst() {
        assertEquals(
                "com.google.android.instantapps.devman",
                myApksInstaller.getApks().get(0).getPkgName());
    }

    @Test
    public void testAllInstalled() throws Exception {
        IDevice device = new InstantAppSdkTests.DeviceGenerator().getDevice();
        myApksInstaller.installAll(
                device, new ProvisionRunner.ProvisionState(), new ProvisionListener.NullListener());

        verify(device, times(4)).installPackage(any(), eq(true));
    }

    @Test
    public void testInstallWithCache() throws Exception {
        IDevice device = new InstantAppSdkTests.DeviceGenerator().getDevice();
        ProvisionRunner.ProvisionState provisionState = new ProvisionRunner.ProvisionState();
        provisionState.lastInstalled = 1;
        myApksInstaller.installAll(device, provisionState, new ProvisionListener.NullListener());

        assertEquals(3, provisionState.lastInstalled);
        verify(device, times(2)).installPackage(any(), eq(true));
    }

    @Test
    public void testNotInstalledWhenVersionIsHigher() throws Throwable {
        IDevice device =
                new InstantAppSdkTests.DeviceGenerator()
                        .setVersionOfPackage("com.google.android.instantapps.devman", 37)
                        .setVersionOfPackage("com.google.android.gms", 11034440 + 1)
                        .getDevice();
        myApksInstaller.installAll(
                device, new ProvisionRunner.ProvisionState(), new ProvisionListener.NullListener());

        verify(device, times(0))
                .installPackage(
                        eq(
                                FileUtils.join(
                                                myInstantAppSdk,
                                                "tools",
                                                "apks",
                                                "release",
                                                "devman.apk")
                                        .getPath()),
                        eq(true));
        verify(device, times(1))
                .installPackage(
                        eq(
                                FileUtils.join(
                                                myInstantAppSdk,
                                                "tools",
                                                "apks",
                                                "release",
                                                "supervisor_x86.apk")
                                        .getPath()),
                        eq(true));
        verify(device, times(0))
                .installPackage(
                        eq(
                                FileUtils.join(
                                                myInstantAppSdk,
                                                "tools",
                                                "apks",
                                                "debug",
                                                "GmsCore_prodmnc_xxhdpi_x86.apk")
                                        .getPath()),
                        eq(true));
        verify(device, times(0))
                .installPackage(
                        eq(
                                FileUtils.join(
                                                myInstantAppSdk,
                                                "tools",
                                                "apks",
                                                "release",
                                                "GmsCore_prodmnc_xxhdpi_x86.apk")
                                        .getPath()),
                        eq(true));
    }
}
