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
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.FileDiff;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;

public class ApkDifferTest {

  @Test
  public void testNoDiff() throws DeployerException {
    ApkDiffer differ = new ApkDiffer();

    List<ApkEntry> before = new ArrayList<>();
    Apk apk0 = new Apk("apk.apk", "abcd", "", ImmutableList.of());
    before.add(new ApkEntry("dex0", 0x01, apk0));

    List<ApkEntry> after = new ArrayList<>();
    Apk apk1 = new Apk("apk.apk", "efgh", "", ImmutableList.of());
    after.add(new ApkEntry("dex0", 0x01, apk1));

    List<FileDiff> diff = differ.diff(before, after);
    assertTrue(diff.isEmpty());

    diff = differ.diff(after, before);
    assertTrue(diff.isEmpty());
  }

  @Test
  public void testMoreApks() {
    ApkDiffer differ = new ApkDiffer();

    List<ApkEntry> before = new ArrayList<>();
    Apk apk1 = new Apk("apk1.apk", "abcd", "", ImmutableList.of());
    before.add(new ApkEntry("dex0", 0x01, apk1));
    Apk apk2 = new Apk("apk2.apk", "abcd", "", ImmutableList.of());
    before.add(new ApkEntry("dex0", 0x01, apk2));

    List<ApkEntry> after = new ArrayList<>();
    Apk apk3 = new Apk("apk1.apk", "efgh", "", ImmutableList.of());
    after.add(new ApkEntry("dex0", 0x01, apk3));
    try {
      differ.diff(before, after);
      fail("Diff should't have thrown an exception");
    }
    catch (DeployerException e) {
      assertEquals(DeployerException.Error.DIFFERENT_NUMBER_OF_APKS, e.getError());
    }

    // Diff in the other direction
    try {
      differ.diff(after, before);
      fail("Diff should't have thrown an exception");
    }
    catch (DeployerException e) {
      assertEquals(DeployerException.Error.DIFFERENT_NUMBER_OF_APKS, e.getError());
    }
  }

  @Test
  public void testDifferentNames() {
    ApkDiffer differ = new ApkDiffer();

    List<ApkEntry> before = new ArrayList<>();
    Apk apk1 = new Apk("apk1.apk", "abcd", "", ImmutableList.of());
    before.add(new ApkEntry("dex0", 0x01, apk1));

    List<ApkEntry> after = new ArrayList<>();
    Apk apk2 = new Apk("apk.apk", "efgh", "", ImmutableList.of());
    after.add(new ApkEntry("dex0", 0x01, apk2));
    try {
      differ.diff(before, after);
      fail("Diff should't have thrown an exception");
    }
    catch (DeployerException e) {
      assertEquals(DeployerException.Error.DIFFERENT_NAMES_OF_APKS, e.getError());
    }

    // Diff in the other direction
    try {
      differ.diff(after, before);
      fail("Diff should't have thrown an exception");
    }
    catch (DeployerException e) {
      assertEquals(DeployerException.Error.DIFFERENT_NAMES_OF_APKS, e.getError());
    }
  }


  @Test
  public void testModified() throws DeployerException {
    ApkDiffer differ = new ApkDiffer();

    List<ApkEntry> before = new ArrayList<>();
    Apk apk1 = new Apk("apk.apk", "abcd", "", ImmutableList.of());
    before.add(new ApkEntry("dex0", 0x00, apk1));
    before.add(new ApkEntry("dex1", 0x01, apk1));
    before.add(new ApkEntry("dex2", 0x02, apk1));
    before.add(new ApkEntry("dex3", 0x03, apk1));

    List<ApkEntry> after = new ArrayList<>();
    Apk apk2 = new Apk("apk.apk", "efgh", "", ImmutableList.of());
    after.add(new ApkEntry("dex1", 0x01, apk2));
    after.add(new ApkEntry("dex2", 0x12, apk2));
    after.add(new ApkEntry("dex3", 0x13, apk2));
    after.add(new ApkEntry("dex4", 0x04, apk2));

    List<FileDiff> diff = differ.diff(before, after);
    assertEquals(4, diff.size());
    diff.sort(Comparator.comparing(a -> a.oldFile == null ? "" : a.oldFile.name));
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
    diff = differ.diff(after, before);
    diff.sort(Comparator.comparing(a -> a.oldFile == null ? "" : a.oldFile.name));
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
}