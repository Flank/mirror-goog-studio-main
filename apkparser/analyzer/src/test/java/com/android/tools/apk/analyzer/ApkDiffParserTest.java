/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.apk.analyzer;

import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.testutils.TestResources;
import com.android.tools.apk.analyzer.internal.ApkDiffEntry;
import com.android.tools.apk.analyzer.internal.ApkDiffParser;
import com.android.tools.apk.analyzer.internal.ApkEntry;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.tree.DefaultMutableTreeNode;
import junit.framework.TestCase;
import org.junit.Test;

public class ApkDiffParserTest {
    private ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);

    @Test
    public void testTreeCreation_1v2() throws IOException {
        Path apkRoot1 = TestResources.getFile("/1.apk").toPath();
        Path apkRoot2 = TestResources.getFile("/2.apk").toPath();
        try (ArchiveContext archiveContext1 = Archives.open(apkRoot1, logger);
                ArchiveContext archiveContext2 = Archives.open(apkRoot2, logger)) {
            DefaultMutableTreeNode treeNode =
                    ApkDiffParser.createTreeNode(archiveContext1, archiveContext2);
            TestCase.assertEquals(
                    "1.apk 649 960 311\n"
                            + "  instant-run.zip 0 352 352\n"
                            + "    instant-run/ 0 2 2\n"
                            + "      classes1.dex 0 2 2\n"
                            + "  res/ 6 6 0\n"
                            + "    anim/ 6 6 0\n"
                            + "      fade.xml 6 6 0\n"
                            + "  AndroidManifest.xml 13 13 0\n",
                    dumpTree(treeNode));
        }
    }

    @Test
    public void testTreeCreation_2v1() throws IOException {
        Path apkRoot1 = TestResources.getFile("/1.apk").toPath();
        Path apkRoot2 = TestResources.getFile("/2.apk").toPath();
        try (ArchiveContext archiveContext1 = Archives.open(apkRoot1, logger);
                ArchiveContext archiveContext2 = Archives.open(apkRoot2, logger)) {
            DefaultMutableTreeNode treeNode =
                    ApkDiffParser.createTreeNode(archiveContext2, archiveContext1);
            TestCase.assertEquals(
                    "2.apk 960 649 -311\n"
                            + "  res/ 6 6 0\n"
                            + "    anim/ 6 6 0\n"
                            + "      fade.xml 6 6 0\n"
                            + "  AndroidManifest.xml 13 13 0\n"
                            + "  instant-run.zip 352 0 -352\n"
                            + "    instant-run/ 2 0 -2\n"
                            + "      classes1.dex 2 0 -2\n",
                    dumpTree(treeNode));
        }
    }

    private static String dumpTree(@NonNull DefaultMutableTreeNode treeNode) {
        StringBuilder sb = new StringBuilder(30);
        dumpTree(sb, treeNode, 0);
        return sb.toString();
    }

    private static void dumpTree(
            @NonNull StringBuilder sb, @NonNull DefaultMutableTreeNode treeNode, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        ApkDiffEntry entry = (ApkDiffEntry) ApkEntry.fromNode(treeNode);
        assertNotNull(entry);
        sb.append(entry.getName());
        sb.append(' ');
        sb.append(entry.getOldSize());
        sb.append(' ');
        sb.append(entry.getNewSize());
        sb.append(' ');
        sb.append(entry.getSize());
        sb.append('\n');

        for (int i = 0; i < treeNode.getChildCount(); i++) {
            dumpTree(sb, (DefaultMutableTreeNode) treeNode.getChildAt(i), depth + 1);
        }
    }
}
