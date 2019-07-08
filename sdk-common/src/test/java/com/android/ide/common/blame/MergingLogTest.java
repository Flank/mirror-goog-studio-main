/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.ide.common.blame;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MergingLogTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void testMergingLog() throws IOException {

        final SourceFilePosition position1 = new SourceFilePosition(
                new SourceFile(absoluteFile("exploded/a/values/values.xml")),
                new SourcePosition(7, 8, 20));

        final SourceFilePosition position2 = new SourceFilePosition(
                new SourceFile(absoluteFile("exploded/b/values/values.xml")),
                new SourcePosition(2, 3, 14));

        File tempDir = mTemporaryFolder.newFolder();
        MergingLog mergingLog = new MergingLog(tempDir);

        mergingLog.logCopy(absoluteFile("exploded/layout/a"), absoluteFile("merged/layout/a"));
        mergingLog.logCopy(absoluteFile("exploded/layout-land/a"),
                absoluteFile("merged/layout-land/a"));

        Map<SourcePosition, SourceFilePosition> map = Maps.newLinkedHashMap();
        map.put(new SourcePosition(1, 2, 3, 7, 1, 120), position1);
        map.put(new SourcePosition(4, 1, 34, 6, 20, 100), position2);
        mergingLog.logSource(new SourceFile(absoluteFile("merged/values/values.xml")), map);

        Map<SourcePosition, SourceFilePosition> map2 = Maps.newLinkedHashMap();
        map2.put(
                new SourcePosition(3, 4, 34),
                new SourceFilePosition(
                        new SourceFile(absoluteFile("exploded/values-de/values.xml")),
                        new SourcePosition(0, 5, 5)));
        mergingLog.logSource(new SourceFile(absoluteFile("merged/values-de/values.xml")), map2);

        // Write and then reload (won't load anything immediately).
        mergingLog.write();
        mergingLog = new MergingLog(tempDir);

        mergingLog.logRemove(new SourceFile(absoluteFile("merged/layout/a")));

        mergingLog.write();
        mergingLog = new MergingLog(tempDir);

        assertThat(mergingLog.find(new SourceFile(absoluteFile("merged/layout-land/a"))))
                .isEqualTo(new SourceFile(absoluteFile("exploded/layout-land/a")));

        /*
          Test
           |---search query----|
          |---------target----------|
        */
        assertThat(
                        mergingLog.find(
                                new SourceFilePosition(
                                        new SourceFile(absoluteFile("merged/values/values.xml")),
                                        new SourcePosition(4, 1, 35, 4, 2, 36))))
                .isEqualTo(position2);

        assertThat(
                        mergingLog.find(
                                new SourceFilePosition(
                                        new SourceFile(absoluteFile("merged/values/values.xml")),
                                        new SourcePosition(4, -1, -1, 6, -1, -1))))
                .isEqualTo(position2);

        /*
         * Test
         *
         *   |search query|
         * |---------target----------|
         *                  |-wrong-|
         */
        assertThat(
                        mergingLog.find(
                                new SourceFilePosition(
                                        new SourceFile(absoluteFile("merged/values/values.xml")),
                                        new SourcePosition(3, -1, -1, 3, -1, -1))))
                .isEqualTo(position1);

        /*
         * Test
         *
         *                   |search query|
         *                 |---------target----------|
         *     |-wrong-|
         */
        assertThat(
                        mergingLog.find(
                                new SourceFilePosition(
                                        new SourceFile(absoluteFile("merged/values/values.xml")),
                                        new SourcePosition(5, -1, -1, 5, -1, -1))))
                .isEqualTo(position2);

        /*
          Test
                     |---search query----|
          |------------target-------------|
                  |-----wrong----|
        */
        assertThat(
                        mergingLog.find(
                                new SourceFilePosition(
                                        new SourceFile(absoluteFile("merged/values/values.xml")),
                                        new SourcePosition(5, 20, 35, 6, 25, 105))))
                .isEqualTo(position1);

        // Check that an unknown file returns itself.
        SourceFilePosition noMatch1 = new SourceFilePosition(
                new SourceFile(absoluteFile("unknownFile")),
                new SourcePosition(1, 2, 3));
        assertThat(mergingLog.find(noMatch1)).isEqualTo(noMatch1);

        // And that a position that is not mapped in the file also returns itself.
        SourceFilePosition noMatch2 = new SourceFilePosition(
                new SourceFile(absoluteFile("merged/values/values.xml")),
                new SourcePosition(100, 0, 3000));
        assertThat(mergingLog.find(noMatch2)).isEqualTo(noMatch2);

        mergingLog.write();
    }

    @Test
    public void testMinimalPersistence() throws IOException {
        SourcePosition sourcePosition1 = new SourcePosition(7, 8, 20);
        File sourceFile1 = absoluteFile("exploded/a/values/values.xml");
        final SourceFilePosition position1 =
                new SourceFilePosition(new SourceFile(sourceFile1), sourcePosition1);

        SourcePosition sourcePosition2 = new SourcePosition(2, 3, 14);
        File sourceFile2 = absoluteFile("exploded/b/values/values.xml");
        final SourceFilePosition position2 =
                new SourceFilePosition(new SourceFile(sourceFile2), sourcePosition2);

        File tempDir = mTemporaryFolder.newFolder();
        MergingLog mergingLog = new MergingLog(tempDir);

        Map<SourcePosition, SourceFilePosition> map = Maps.newLinkedHashMap();
        File outputFile = absoluteFile("merged/values/values.xml");
        map.put(sourcePosition1, position1);
        map.put(sourcePosition2, position2);
        mergingLog.logSource(new SourceFile(outputFile), map);

        mergingLog.write();
        assertThat(tempDir.listFiles()).isNotEmpty();
        File expectedLogFile = new File(new File(tempDir, "multi-v2"), "values.json");
        assertThat(expectedLogFile.exists()).isTrue();
        String log = Files.asCharSource(expectedLogFile, Charsets.UTF_8).read();
        assertThat(log).doesNotContain("\"to\"");
        assertThat(log).doesNotContain("endLines");

        // now reload the minimal log and assert that memory model is correct.
        Map<SourceFile, Map<SourcePosition, SourceFilePosition>> values =
                MergingLogPersistUtil.loadFromMultiFileVersion2(tempDir, "values");

        assertThat(values).hasSize(1);
        Map<SourcePosition, SourceFilePosition> loadedMap = values.get(new SourceFile(outputFile));
        assertThat(loadedMap).isNotNull();
        assertThat(loadedMap).hasSize(2);

        SourceFilePosition sourceFilePosition1 = loadedMap.get(sourcePosition1);
        assertThat(sourceFilePosition1).isNotNull();
        assertThat(sourceFilePosition1.getPosition()).isEqualTo(sourcePosition1);
        assertThat(sourceFilePosition1.getFile().getSourceFile()).isEqualTo(sourceFile1);
        assertThat(sourceFilePosition1.getPosition().getEndLine())
                .isEqualTo(position1.getPosition().getEndLine());

        SourceFilePosition sourceFilePosition2 = loadedMap.get(sourcePosition2);
        assertThat(sourceFilePosition2).isNotNull();
        assertThat(sourceFilePosition2.getPosition()).isEqualTo(sourcePosition2);
        assertThat(sourceFilePosition2.getFile().getSourceFile()).isEqualTo(sourceFile2);
        assertThat(sourceFilePosition2.getPosition().getEndLine())
                .isEqualTo(position2.getPosition().getEndLine());
    }

    private File testPath;

    @Before
    public void setupTestPath() throws IOException {
        testPath = mTemporaryFolder.newFolder();
    }

    private File absoluteFile(String path) {
        return new File(testPath, path);
    }
}
