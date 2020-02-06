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
import com.android.tools.deployer.model.DexClass;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class OverlayIdTest {
    @Test
    public void testRealInstall() throws DeployerException {
        OverlayId realInstall = new OverlayId(makeApks(1));
        String stringRep = realInstall.getRepresentation();
        Assert.assertEquals("Real APK ApkName0 has checksum of ApkChecksum0\n", stringRep);

        realInstall = new OverlayId(makeApks(1));
        String sha = realInstall.getSha();
        Assert.assertEquals(
                "6a94b1483bd168a5a81097891ca3afa3e2ebbb499af3064950a87d93e39ab913", sha);
    }

    @Test
    public void testSwaps() throws Exception {

        // *****************************************************
        // Fresh install
        // *****************************************************
        OverlayId realInstall = new OverlayId(makeApks(2));
        String stringRep = realInstall.getRepresentation();
        Assert.assertEquals(
                "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n",
                stringRep);
        String sha = realInstall.getSha();
        Assert.assertEquals(
                "6d19902d86f5115131af14f9c35340916fd34cd5910c68c9afe7be2c02a7f07f", sha);

        // *****************************************************
        // First IWI Swap
        // Doing first IWI swap on a real APK, adding a class.
        // *****************************************************
        DexComparator.ChangedClasses changes =
                new DexComparator.ChangedClasses(
                        Lists.newArrayList(new DexClass("dex1", 1l, null, null)),
                        new ArrayList<>());
        OverlayId firstSwap = new OverlayId(realInstall, changes);
        stringRep = firstSwap.getRepresentation();
        Assert.assertEquals(
                "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile dex1.dex with checksum 1\n",
                stringRep);
        sha = firstSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "b213ed2220397f351fc67faf9f6f10defa49a5e2e3906d7c6d5498f9566061b4", sha);

        // *****************************************************
        // Second IWI Swap
        // Doing a IWI swap on the previous IWI install, modifying an existing class.
        // *****************************************************
        changes =
                new DexComparator.ChangedClasses(
                        new ArrayList<>(),
                        Lists.newArrayList(new DexClass("dex2", 2l, null, null)));
        OverlayId secondSwap = new OverlayId(firstSwap, changes);
        stringRep = secondSwap.getRepresentation();
        Assert.assertEquals(
                "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile dex1.dex with checksum 1\n"
                        + " Has overlayfile dex2.dex with checksum 2\n",
                stringRep);
        sha = secondSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "b45cfffe85db65016781e516cc3cefa8736f0165be14eb5635f2bfd8945859a1", sha);

        // *****************************************************
        // Third IWI Swap
        // Doing a IWI swap on the previous IWI install, modifying dex2 again.
        // *****************************************************
        changes =
                new DexComparator.ChangedClasses(
                        new ArrayList<>(),
                        Lists.newArrayList(new DexClass("dex2", 200l, null, null)));
        OverlayId thirdSwap = new OverlayId(secondSwap, changes);
        stringRep = thirdSwap.getRepresentation();
        Assert.assertEquals(
                "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile dex1.dex with checksum 1\n"
                        + " Has overlayfile dex2.dex with checksum 200\n",
                stringRep);
        sha = thirdSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "febeaca44e2aa6f3c78133aec0f0c747e098e73329cd722a5006de39ae349e2f", sha);
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
}
