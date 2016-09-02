/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dex;

import static org.junit.Assert.assertTrue;

import com.android.dx.command.dexer.Main;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class InProcessDexTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void checkDexAreReadFromDirAndCopiedToOutput() throws Exception {
        File dexDir = copyFromDexResourcesToDir("app.dex");

        long startSize = new File(dexDir, "classes.dex").length();

        File output = mTemporaryFolder.newFolder("output");
        String[] args = new String[]{
                "--output=" + output.getPath(), dexDir.getPath()
        };
        Main.main(args);

        File outputDex = new File(output, "classes.dex");
        assertTrue(outputDex.exists());
        assertTrue(startSize == outputDex.length());
    }

    @Test
    public void checkDexAreReadFromDirAndMerged() throws Exception {
        File incrementalDexDir = copyFromDexResourcesToDir("app.dex");
        File hamcrestDexDir = copyFromDexResourcesToDir("hamcrest.dex");

        File incrementalDex = new File(incrementalDexDir, "classes.dex");
        long startSize = incrementalDex.length();

        String[] args = new String[]{
                "--incremental", "--output=" + incrementalDex.getPath(), hamcrestDexDir.getPath()
        };
        Main.main(args);

        assertTrue(startSize < incrementalDex.length());
    }

    private File copyFromDexResourcesToDir(String resourceName) throws IOException {
        File testFolder = mTemporaryFolder.newFolder();
        Files.copy(
                InProcessDexTest.class.getResourceAsStream("/testData/dex/" + resourceName),
                testFolder.toPath().resolve("classes.dex"));

        return testFolder;
    }
}
