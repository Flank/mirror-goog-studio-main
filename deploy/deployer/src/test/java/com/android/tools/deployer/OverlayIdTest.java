/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.deployer;

import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class OverlayIdTest {
    @Test
    public void testRealInstall() throws DeployerException {
        OverlayId realInstall = new OverlayId(makeApks(1));
        String stringRep = realInstall.getRepresentation();
        Assert.assertEquals(
                getFileHeader() + "Real APK ApkName0 has checksum of ApkChecksum0\n", stringRep);

        realInstall = new OverlayId(makeApks(1));
        String sha = realInstall.getSha();
        Assert.assertEquals(
                "3af200fe91ea16478c0dee3561a59a14f3abb3d0eaaad049a95f88262b0c102e", sha);
    }

    @Test
    public void testSwaps() throws Exception {

        // *****************************************************
        // Fresh install
        // *****************************************************
        OverlayId realInstall = new OverlayId(makeApks(2));
        String stringRep = realInstall.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n",
                stringRep);
        String sha = realInstall.getSha();
        Assert.assertEquals(
                "572bf1f3899e02da013865345cd1491b050542347784865bf040e6b936bb70f4", sha);

        // *****************************************************
        // First IWI Swap
        // Doing first IWI swap on a real APK, adding a class.
        // *****************************************************
        DexComparator.ChangedClasses changes =
                new DexComparator.ChangedClasses(
                        Lists.newArrayList(new DexClass("dex1", 1l, null, null)),
                        new ArrayList<>());
        OverlayId firstSwap = new OverlayId(realInstall, changes, ImmutableSet.of());
        stringRep = firstSwap.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile dex1.dex with checksum 1\n",
                stringRep);
        sha = firstSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "f63e09245e5182e0406fa46798d1a159d4964835ecf6fa444aab8d60f06ca790", sha);

        // *****************************************************
        // Second IWI Swap
        // Doing a IWI swap on the previous IWI install, modifying an existing class.
        // *****************************************************
        changes =
                new DexComparator.ChangedClasses(
                        new ArrayList<>(),
                        Lists.newArrayList(new DexClass("dex2", 2l, null, null)));
        OverlayId secondSwap = new OverlayId(firstSwap, changes, ImmutableSet.of());
        stringRep = secondSwap.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile dex1.dex with checksum 1\n"
                        + " Has overlayfile dex2.dex with checksum 2\n",
                stringRep);
        sha = secondSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "82e4ddc3d0609c877f94f3f490da498afb7fa646412c9e3ec00aa41f34fad096", sha);

        // *****************************************************
        // Third IWI Swap
        // Doing a IWI swap on the previous IWI install, modifying dex2 again.
        // *****************************************************
        changes =
                new DexComparator.ChangedClasses(
                        new ArrayList<>(),
                        Lists.newArrayList(new DexClass("dex2", 200l, null, null)));
        OverlayId thirdSwap = new OverlayId(secondSwap, changes, ImmutableSet.of());
        stringRep = thirdSwap.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile dex1.dex with checksum 1\n"
                        + " Has overlayfile dex2.dex with checksum 200\n",
                stringRep);
        sha = thirdSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "de0ca2bc6919267e81741eeb93ec7990977e8406b6fdddc441c6b8f50ae418a6", sha);
    }

    @Test
    public void testResourceSwaps() throws Exception {

        // *****************************************************
        // Fresh install
        // *****************************************************
        OverlayId realInstall = new OverlayId(makeApks(2));
        String stringRep = realInstall.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n",
                stringRep);
        String sha = realInstall.getSha();
        Assert.assertEquals(
                "572bf1f3899e02da013865345cd1491b050542347784865bf040e6b936bb70f4", sha);

        // *****************************************************
        // First IWI Swap
        // Doing first IWI swap on a real APK, adding a resource.
        // *****************************************************
        DexComparator.ChangedClasses dexChanges =
                new DexComparator.ChangedClasses(ImmutableList.of(), ImmutableList.of());
        Apk resApk = Apk.builder().setName("apk").build();
        Set<ApkEntry> resChanges = ImmutableSet.of(new ApkEntry("res/1", 1, resApk));
        OverlayId firstSwap = new OverlayId(realInstall, dexChanges, resChanges);
        stringRep = firstSwap.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile apk/res/1 with checksum 1\n",
                stringRep);
        sha = firstSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "b41e7471c56edaf8ca8932865673fa1db1ab9499b755d2067bc7a09bd89fd217", sha);

        // *****************************************************
        // Second IWI Swap
        // Doing a IWI swap on the previous IWI install, modifying an existing resource.
        // *****************************************************
        resChanges = ImmutableSet.of(new ApkEntry("res/1", 11, resApk));
        OverlayId secondSwap = new OverlayId(firstSwap, dexChanges, resChanges);
        stringRep = secondSwap.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile apk/res/1 with checksum 11\n",
                stringRep);
        sha = secondSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "0cd8fda9b3a54e1641f3b4f5239b8e53405159543525424940fb3b57b44f4eaa", sha);

        // *****************************************************
        // Third IWI Swap
        // Doing a IWI swap on the previous IWI install, adding another resource.
        // *****************************************************

        resChanges = ImmutableSet.of(new ApkEntry("res/2", 2, resApk));
        OverlayId thirdSwap = new OverlayId(secondSwap, dexChanges, resChanges);
        stringRep = thirdSwap.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile apk/res/1 with checksum 11\n"
                        + " Has overlayfile apk/res/2 with checksum 2\n",
                stringRep);
        sha = thirdSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "633b0d701f88a7abe60abcdf12d542dba4930302d6106ba5a5687e1f79c50785", sha);
    }

    private List<Apk> makeApks(int size) {
        List<Apk> apks = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            apks.add(
                    Apk.builder()
                            .setName("ApkName" + i)
                            .setChecksum("ApkChecksum" + i)
                            .setPackageName("overlay.id.test")
                            .setPath("/some/path")
                            .build());
        }
        return apks;
    }

    private static String getFileHeader() {
        return "Apply Changes Overlay ID\n" + "Schema Version " + OverlayId.SCHEMA_VERSION + "\n";
    }
}
