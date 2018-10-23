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
package com.android.ide.common.resources;

import static com.android.ide.common.resources.DataFile.FileType.XML_VALUES;
import static com.android.ide.common.resources.DataMerger.NODE_DATA_SET;
import static com.android.ide.common.resources.DataMerger.NODE_MERGER;
import static com.google.common.truth.Truth.assertThat;
import static java.io.File.separator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.TestUtils;
import com.android.utils.ILogger;
import com.android.utils.XmlUtils;
import java.io.File;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class DataSetTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void testIsIgnored() throws Exception {
        DataSet dataSet = getDataSet();

        assertFalse(dataSet.isIgnored(new File("a.")));
        assertFalse(dataSet.isIgnored(new File("foo")));
        assertFalse(dataSet.isIgnored(new File("foo" + separator + "bar")));
        assertFalse(dataSet.isIgnored(new File("foo")));
        assertFalse(dataSet.isIgnored(new File("foo" + separator + "bar")));
        assertFalse(dataSet.isIgnored(new File("layout" + separator + "main.xml")));
        assertFalse(dataSet.isIgnored(new File("res" + separator + "drawable" + separator + "foo.png")));
        assertFalse(dataSet.isIgnored(new File("")));

        assertTrue(dataSet.isIgnored(new File(".")));
        assertTrue(dataSet.isIgnored(new File("..")));
        assertTrue(dataSet.isIgnored(new File(".git")));
        assertTrue(dataSet.isIgnored(new File("foo" + separator + ".git")));
        assertTrue(dataSet.isIgnored(new File(".svn")));
        assertTrue(dataSet.isIgnored(new File("thumbs.db")));
        assertTrue(dataSet.isIgnored(new File("Thumbs.db")));
        assertTrue(dataSet.isIgnored(new File("foo" + separator + "Thumbs.db")));

        // Suffix
        assertTrue(dataSet.isIgnored(new File("foo~")));
        assertTrue(dataSet.isIgnored(new File("foo.scc")));
        assertTrue(dataSet.isIgnored(new File("foo" + separator + "foo.scc")));

        // Prefix
        assertTrue(dataSet.isIgnored(new File(".test")));
        assertTrue(dataSet.isIgnored(new File("foo" + separator + ".test")));

        // Don't match on non-directory
        assertFalse(dataSet.isIgnored(new File("_test")));
        File dir = new File(TestUtils.createTempDirDeletedOnExit(), "_test");
        assertTrue(dir.mkdirs());
        assertTrue(dataSet.isIgnored(dir));
    }

    @Test
    public void testLongestPath() {
        DataSet dataSet = getDataSet();

        File res = new File(mTemporaryFolder.getRoot(), "res");
        assertTrue(res.mkdirs());

        File foo = new File(mTemporaryFolder.getRoot(), "foo");
        assertTrue(foo.mkdirs());

        File customRes = new File(mTemporaryFolder.getRoot(), "res/layouts/shared");
        assertTrue(customRes.mkdirs());

        dataSet.addSource(res);
        dataSet.addSource(foo);
        dataSet.addSource(customRes);

        assertEquals(res.getAbsolutePath(), dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(), "res/any.xml")).getPath());
        assertEquals(res.getAbsolutePath(), dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(), "res/layout/activity.xml")).getPath());
        assertEquals(res.getAbsolutePath(), dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(), "res/layout/foo/bar/activity.xml")).getPath());
        assertEquals(customRes.getAbsolutePath(), dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(), "res/layouts/shared/any.xml")).getPath());
        assertEquals(customRes.getAbsolutePath(), dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(),
                        "res/layouts/shared/layout/activity.xml")).getPath());
        assertEquals(customRes.getAbsolutePath(), dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(),
                        "res/layouts/shared/layout/foo/bar/activity.xml")).getPath());
        assertNull(dataSet.findMatchingSourceFile(
                new File(mTemporaryFolder.getRoot(), "funky/shared/layout/foo/bar/activity.xml")));
    }

    @Test
    public void testWritingXml() throws IOException {
        DataSet dataSet = getDataSet();

        File notEmpty = mTemporaryFolder.newFile("notEmpty.xml");
        DataFile notEmptyDataFile = new DataFile(notEmpty, XML_VALUES) {};
        notEmptyDataFile.addItem(new DataItem("item") {});
        dataSet.addDataFile(notEmpty, notEmptyDataFile);
        dataSet.addSource(notEmpty);

        File empty = mTemporaryFolder.newFile("empty.xml");
        DataFile emptyDataFile = new DataFile(empty, XML_VALUES) {};
        dataSet.addDataFile(empty, emptyDataFile);
        dataSet.addSource(empty);

        Document document = XmlUtils.createDocument(false);
        Node root = document.createElement(NODE_MERGER);
        document.appendChild(root);

        Node dataSetNode = document.createElement(NODE_DATA_SET);
        root.appendChild(dataSetNode);

        dataSet.appendToXml(dataSetNode, document, getFakeMergeConsumer(), false);
        String content = XmlUtils.toXml(document);

        assertThat(content).contains("file path=\"" + notEmpty.getAbsolutePath() + "\"");
        assertThat(content).contains("file path=\"" + empty.getAbsolutePath() + "\"");
    }

    private static DataSet getDataSet() {
        return new DataSet("foo", false) {
            @Override
            @NonNull
            protected DataSet createSet(@NonNull String name) {
                return null;
            }

            @Override
            protected DataFile createFileAndItemsFromXml(@NonNull File file, @NonNull Node fileNode)
                    throws MergingException {
                return null;
            }

            @Override
            protected void readSourceFolder(File sourceFolder, ILogger logger)
                    throws MergingException {}

            @Override
            @Nullable
            protected DataFile createFileAndItems(File sourceFolder, File file, ILogger logger)
                    throws MergingException {
                return null;
            }
        };
    }

    private static MergeConsumer getFakeMergeConsumer() {
        return new MergeConsumer() {
            @Override
            public void start(@NonNull DocumentBuilderFactory factory) throws ConsumerException {}

            @Override
            public void end() throws ConsumerException {}

            @Override
            public void addItem(@NonNull DataItem item) throws ConsumerException {}

            @Override
            public void removeItem(@NonNull DataItem removedItem, @Nullable DataItem replacedBy)
                    throws ConsumerException {}

            @Override
            public boolean ignoreItemInMerge(DataItem item) {
                return false;
            }
        };
    }
}