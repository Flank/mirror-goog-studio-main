/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.*;

import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.FileDiff;
import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

public class ApkDifferTest {
  @Test
  public void testNoDiff() throws DeployerException {
        Apk before =
                Apk.builder()
                        .setName("apk.apk")
                        .setChecksum("abcd")
                        .addApkEntry("dex0", 0x01)
                        .build();

        Apk after =
                Apk.builder()
                        .setName("apk.apk")
                        .setChecksum("efgh")
                        .addApkEntry("dex0", 0x01)
                        .build();

        List<FileDiff> diff = checkDiffs(before, after);
        assertTrue(diff.isEmpty());

        diff = checkDiffs(after, before);
        assertTrue(diff.isEmpty());
  }

  @Test
  public void testMoreApks() {
        Apk apk1 =
                Apk.builder()
                        .setName("apk1.apk")
                        .setChecksum("abcd")
                        .addApkEntry("dex0", 0x01)
                        .build();

        Apk apk2 =
                Apk.builder()
                        .setName("apk2.apk")
                        .setChecksum("abcd")
                        .addApkEntry("dex0", 0x01)
                        .build();

        Apk apk3 =
                Apk.builder()
                        .setName("apk1.apk")
                        .setChecksum("efgh")
                        .addApkEntry("dex0", 0x01)
                        .build();
    try {
            checkDiffs(ImmutableList.of(apk1, apk2), ImmutableList.of(apk3));
            fail("Diff should have thrown an exception");
    }
    catch (DeployerException e) {
      assertEquals(DeployerException.Error.DIFFERENT_NUMBER_OF_APKS, e.getError());
    }

    // Diff in the other direction
    try {
            checkDiffs(ImmutableList.of(apk3), ImmutableList.of(apk1, apk2));
            fail("Diff should have thrown an exception");
    }
    catch (DeployerException e) {
      assertEquals(DeployerException.Error.DIFFERENT_NUMBER_OF_APKS, e.getError());
    }
  }

  @Test
  public void testDifferentNames() {
        Apk before =
                Apk.builder()
                        .setName("apk1.apk")
                        .setChecksum("abcd")
                        .addApkEntry("dex0", 0x01)
                        .build();

        Apk after =
                Apk.builder()
                        .setName("apk.apk")
                        .setChecksum("abcd")
                        .addApkEntry("dex0", 0x01)
                        .build();

    try {
            checkDiffs(before, after);
            fail("Diff should have thrown an exception");
    }
    catch (DeployerException e) {
            assertEquals(DeployerException.Error.DIFFERENT_APK_NAMES, e.getError());
    }

    // Diff in the other direction
    try {
            checkDiffs(after, before);
            fail("Diff should have thrown an exception");
    }
    catch (DeployerException e) {
            assertEquals(DeployerException.Error.DIFFERENT_APK_NAMES, e.getError());
    }
  }


  @Test
  public void testModified() throws DeployerException {
        Apk apk1 =
                Apk.builder()
                        .setName("apk.apk")
                        .setChecksum("abcd")
                        .addApkEntry("dex0", 0x00)
                        .addApkEntry("dex1", 0x01)
                        .addApkEntry("dex2", 0x02)
                        .addApkEntry("dex3", 0x03)
                        .build();

        Apk apk2 =
                Apk.builder()
                        .setName("apk.apk")
                        .setChecksum("efgh")
                        .addApkEntry("dex1", 0x01)
                        .addApkEntry("dex2", 0x12)
                        .addApkEntry("dex3", 0x13)
                        .addApkEntry("dex4", 0x04)
                        .build();

        List<FileDiff> diff = checkDiffs(apk1, apk2);
        assertEquals(4, diff.size());
        diff.sort(Comparator.comparing(a -> a.oldFile == null ? "" : a.oldFile.getName()));
        assertEquals(FileDiff.Status.CREATED, diff.get(0).status);
        ApkTestUtils.assertApkEntryEquals(apk2.checksum, "dex4", 0x04, diff.get(0).newFile);

        assertEquals(FileDiff.Status.DELETED, diff.get(1).status);
        ApkTestUtils.assertApkEntryEquals(apk1.checksum, "dex0", 0x00, diff.get(1).oldFile);

        assertEquals(FileDiff.Status.MODIFIED, diff.get(2).status);
        ApkTestUtils.assertApkEntryEquals(apk1.checksum, "dex2", 0x02, diff.get(2).oldFile);
        ApkTestUtils.assertApkEntryEquals(apk2.checksum, "dex2", 0x12, diff.get(2).newFile);

        assertEquals(FileDiff.Status.MODIFIED, diff.get(3).status);
        ApkTestUtils.assertApkEntryEquals(apk1.checksum, "dex3", 0x03, diff.get(3).oldFile);
        ApkTestUtils.assertApkEntryEquals(apk2.checksum, "dex3", 0x13, diff.get(3).newFile);

        // Diff in the other direction
        diff = checkDiffs(apk2, apk1);
        diff.sort(Comparator.comparing(a -> a.oldFile == null ? "" : a.oldFile.getName()));
        assertEquals(FileDiff.Status.CREATED, diff.get(0).status);
        ApkTestUtils.assertApkEntryEquals(apk1.checksum, "dex0", 0x00, diff.get(0).newFile);

        assertEquals(FileDiff.Status.MODIFIED, diff.get(1).status);
        ApkTestUtils.assertApkEntryEquals(apk2.checksum, "dex2", 0x12, diff.get(1).oldFile);
        ApkTestUtils.assertApkEntryEquals(apk1.checksum, "dex2", 0x02, diff.get(1).newFile);

        assertEquals(FileDiff.Status.MODIFIED, diff.get(2).status);
        ApkTestUtils.assertApkEntryEquals(apk2.checksum, "dex3", 0x13, diff.get(2).oldFile);
        ApkTestUtils.assertApkEntryEquals(apk1.checksum, "dex3", 0x03, diff.get(2).newFile);

        assertEquals(FileDiff.Status.DELETED, diff.get(3).status);
        ApkTestUtils.assertApkEntryEquals(apk2.checksum, "dex4", 0x04, diff.get(3).oldFile);
    }

    @Test
    public void testSpecDiffNoChange() throws DeployerException {
        Apk before =
                Apk.builder()
                        .setName("apk.apk")
                        .setChecksum("abcd")
                        .addApkEntry("res/a.xml", 0x01)
                        .build();

        Apk after =
                Apk.builder()
                        .setName("apk.apk")
                        .setChecksum("efgh")
                        .addApkEntry("res/a.xml", 0x01)
                        .build();

        List<FileDiff> diff = checkSpecDiffs(before, after);
        assertEquals(1, diff.size());
        assertEquals(FileDiff.Status.RESOURCE_NOT_IN_OVERLAY, diff.get(0).status);
    }

    @Test
    public void testSpecDiffResourceChanged() throws DeployerException {
        Apk before =
                Apk.builder()
                        .setName("apk.apk")
                        .setChecksum("abcd")
                        .addApkEntry("res/a.xml", 0x01)
                        .build();

        Apk after =
                Apk.builder()
                        .setName("apk.apk")
                        .setChecksum("efgh")
                        .addApkEntry("res/a.xml", 0x02)
                        .build();

        List<FileDiff> diff = checkSpecDiffs(before, after);
        assertEquals(1, diff.size());
        assertEquals(FileDiff.Status.MODIFIED, diff.get(0).status);
    }

    @Test
    public void testSpecDiffDeletedResource() throws DeployerException {
        Apk before =
                Apk.builder()
                        .setName("apk.apk")
                        .setChecksum("abcd")
                        .addApkEntry("res/a.xml", 0x01)
                        .addApkEntry("res/b.xml", 0x02)
                        .build();

        Apk after =
                Apk.builder()
                        .setName("apk.apk")
                        .setChecksum("efgh")
                        .addApkEntry("res/a.xml", 0x01)
                        .build();

        List<FileDiff> diff = checkSpecDiffs(before, after);
        assertEquals(2, diff.size());

        Map<FileDiff.Status, FileDiff> diffMap =
                diff.stream().collect(Collectors.toMap(d -> d.status, d -> d));
        assertTrue(diffMap.containsKey(FileDiff.Status.DELETED));
        assertTrue(diffMap.containsKey(FileDiff.Status.RESOURCE_NOT_IN_OVERLAY));

        assertNull(diffMap.get(FileDiff.Status.DELETED).newFile);
        assertEquals("res/b.xml", diffMap.get(FileDiff.Status.DELETED).oldFile.getName());

        assertNull(diffMap.get(FileDiff.Status.RESOURCE_NOT_IN_OVERLAY).oldFile);
        assertEquals(
                "res/a.xml",
                diffMap.get(FileDiff.Status.RESOURCE_NOT_IN_OVERLAY).newFile.getName());
    }

    private static List<FileDiff> checkDiffs(Apk before, Apk after) throws DeployerException {
        return checkDiffs(ImmutableList.of(before), ImmutableList.of(after));
    }

    private static List<FileDiff> checkDiffs(List<Apk> before, List<Apk> after)
            throws DeployerException {
        ApkDiffer differ = new ApkDiffer();
        return differ.diff(before, after);
    }

    private static List<FileDiff> checkSpecDiffs(Apk before, Apk after) throws DeployerException {
        return checkSpecDiffs(ImmutableList.of(before), ImmutableList.of(after));
    }

    private static List<FileDiff> checkSpecDiffs(List<Apk> before, List<Apk> after)
            throws DeployerException {
        ApkDiffer differ = new ApkDiffer();
        DeploymentCacheDatabase fakeDb = new DeploymentCacheDatabase(1);
        fakeDb.store("serial", "app.id", before, new OverlayId(before));
        return differ.specDiff(fakeDb.get("serial", "app.id"), after);
  }
}