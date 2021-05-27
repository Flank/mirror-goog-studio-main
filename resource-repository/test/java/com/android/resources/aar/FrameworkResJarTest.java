/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.resources.aar;

import com.android.testutils.TestUtils;
import com.android.utils.PathUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.fail;

/**
 * Checks that the {@code prebuilts/studio/layoutlib/data/framework_res.jar} file is up to date.
 * <p>
 * If this test fails, run {@code tools/vendor/google/layoutlib-prebuilt/build_framework_res_jar.sh}
 * to update {@code prebuilts/studio/layoutlib/data/framework_res.jar}.
 */
public class FrameworkResJarTest {
  private Path myTempDir;

  /** Returns the path of framework_res.jar in prebuilts. */
  @NotNull
  private static Path getFrameworkResJar() {
    return TestUtils.resolveWorkspacePath("prebuilts/studio/layoutlib/data/framework_res.jar");
  }

  /** Returns the path of the framework res directory in prebuilts. */
  @NotNull
  private static Path getFrameworkResDir() {
    return TestUtils.resolveWorkspacePath("prebuilts/studio/layoutlib/data/res");
  }

  /** Returns the path of a freshly built framework_res.jar. */
  @NotNull
  private Path getExpectedFrameworkResJar() throws IOException {
    Path path = myTempDir.resolve("framework_res.jar");
    FrameworkResJarCreator.createJar(getFrameworkResDir(), path);
    return path;
  }

  private static void frameworkResJarIsOutOfDate(@NotNull Path file) {
    fail("The " + file + " file is out of date." +
         " Please run tools/vendor/google/layoutlib-prebuilt/build_framework_res_jar.sh to update it.");
  }

  @Before
  public void setUp() throws Exception {
    myTempDir = Files.createTempDirectory("framework_res");
  }

  @After
  public void tearDown() throws Exception {
    PathUtils.deleteRecursivelyIfExists(myTempDir);
  }

  @Test
  public void testCompareJars() throws Exception {
    Path file = getFrameworkResJar();
    Path expectedFile = getExpectedFrameworkResJar();
    try (ZipFile zip1 = new ZipFile(file.toFile());
         ZipFile zip2 = new ZipFile(expectedFile.toFile())) {
      Enumeration<? extends ZipEntry> entries1 = zip1.entries();
      Enumeration<? extends ZipEntry> entries2 = zip2.entries();
      while (true) {
        boolean hasMore1 = entries1.hasMoreElements();
        boolean hasMore2 = entries1.hasMoreElements();
        if (hasMore1 != hasMore2) {
          frameworkResJarIsOutOfDate(file);
        }
        if (!hasMore1) {
          break;
        }
        ZipEntry entry1 = entries1.nextElement();
        ZipEntry entry2 = entries2.nextElement();
        if (!entry1.getName().equals(entry2.getName())) {
          frameworkResJarIsOutOfDate(file);
        }
        if (entry1.getMethod() != entry2.getMethod()) {
          frameworkResJarIsOutOfDate(file);
        }
        byte[] buf1 = new byte[8192];
        byte[] buf2 = new byte[8192];
        try (InputStream stream1 = zip1.getInputStream(entry1);
             InputStream stream2 = zip2.getInputStream(entry2)) {
          while (true) {
            int n1 = stream1.read(buf1);
            int n2 = stream2.read(buf2);
            if (n1 != n2) {
              frameworkResJarIsOutOfDate(file);
            }
            if (n1 < 0) {
              break;
            }
            for (int i = 0; i < n1; i++) {
              if (buf1[i] != buf2[i]) {
                frameworkResJarIsOutOfDate(file);
              }
            }
          }
        }
      }
    }
  }
}
