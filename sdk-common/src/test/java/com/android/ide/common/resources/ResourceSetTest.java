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

import static com.google.common.truth.Truth.assertThat;
import static java.io.File.separator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.testutils.TestResources;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class ResourceSetTest extends BaseTestCase {

    @Test
    public void testBaseResourceSetByCount() throws Exception {
        ResourceSet resourceSet = getBaseResourceSet();
        assertEquals(34, resourceSet.size());
    }

    @Test
    public void testBaseResourceSetWithNormalizationByName() throws Exception {
        ResourceSet resourceSet = getBaseResourceSet();

        verifyResourceExists(
                resourceSet,
                "drawable/icon",
                "drawable/patch",
                "raw/foo",
                "layout/main",
                "layout/layout_ref",
                "layout/alias_replaced_by_file",
                "layout/file_replaced_by_alias",
                "drawable/color_drawable",
                "drawable/drawable_ref",
                "color/color",
                "string/basic_string",
                "string/xliff_string",
                "string/styled_string",
                "string/two",
                "string/many",
                "style/style",
                "array/string_array",
                "array/integer_array",
                "array/my_colors",
                "attr/dimen_attr",
                "attr/string_attr",
                "attr/enum_attr",
                "attr/flag_attr",
                "attr/blah",
                "attr/blah2",
                "attr/flagAttr",
                "styleable/declare_styleable",
                "dimen/dimen",
                "dimen-sw600dp-v13/offset",
                "id/item_id",
                "integer/integer",
                "plurals/plurals",
                "plurals/plurals_with_bad_quantity");
    }

    @Test
    public void testDupResourceSet() throws Exception {
        File root = TestResources.getDirectory(getClass(), "/testData/resources/dupSet");

        ResourceSet set = createResourceSet();
        set.addSource(new File(root, "res1"));
        set.addSource(new File(root, "res2"));
        boolean gotException = false;
        RecordingLogger logger =  new RecordingLogger();
        try {
            set.loadFromFiles(logger);
        } catch (DuplicateDataException e) {
            gotException = true;




            String message = e.getMessage();
            // Clean up paths etc for unit test
            int index = message.indexOf("dupSet");
            assertTrue(index != -1);
            String prefix = message.substring(0, index);
            message = message.replace(prefix, "<PREFIX>").replace('\\','/');
            assertEquals("<PREFIX>dupSet/res1/drawable/icon.png\t<PREFIX>dupSet/res2/drawable/icon.png: "
                    + "Error: Duplicate resources", message);
        }

        checkLogger(logger);
        assertTrue(gotException);
    }

    @Test
    public void testResourceSet_singleFile() throws Exception {
        File resourceFile =
                TestResources.getFile(getClass(), "/testData/resources/baseSet/layout/main.xml");
        ResourceSet set = createResourceSet();
        set.addSource(resourceFile);
        RecordingLogger logger = new RecordingLogger();
        set.loadFromFiles(logger);
        checkLogger(logger);
        assertThat(set.size()).isEqualTo(1);
        assertThat(set.getDataMap().get("layout/main")).isNotEmpty();
    }

    @NonNull
    private static ResourceSet createResourceSet() {
        return new ResourceSet("main", ResourceNamespace.RES_AUTO, null, true);
    }

    @Test
    public void testBrokenSet() throws Exception {
        File root = TestResources.getDirectory(getClass(), "/testData/resources/brokenSet");

        ResourceSet set = createResourceSet();
        set.addSource(root);

        boolean gotException = false;
        RecordingLogger logger =  new RecordingLogger();
        try {
            set.loadFromFiles(logger);
        } catch (MergingException e) {
            gotException = true;
            assertEquals(new File(root, "values" + separator + "dimens.xml").getAbsolutePath() +
                    ":1:1: Error: Content is not allowed in prolog.",
                    e.getMessage());
        }

        assertTrue("ResourceSet processing should have failed, but didn't", gotException);
        assertFalse(logger.getErrorMsgs().isEmpty());
    }

    @Test
    public void testBrokenSet2() throws Exception {
        File root = TestResources.getDirectory(getClass(), "/testData/resources/brokenSet2");

        ResourceSet set = createResourceSet();
        set.addSource(root);

        boolean gotException = false;
        RecordingLogger logger =  new RecordingLogger();
        try {
            set.loadFromFiles(logger);
        } catch (MergingException e) {
            gotException = true;
            assertEquals(new File(root, "values" + separator + "values.xml").getAbsolutePath() +
                    ": Error: Found item String/app_name more than one time",
                    e.getMessage());
        }

        assertTrue("ResourceSet processing should have failed, but didn't", gotException);
        assertFalse(logger.getErrorMsgs().isEmpty());
    }

    @Test
    public void testBrokenSet3() throws Exception {
        File root = TestResources.getDirectory(getClass(), "/testData/resources/brokenSet3");

        ResourceSet set = createResourceSet();
        set.addSource(root);

        boolean gotException = false;
        RecordingLogger logger =  new RecordingLogger();
        try {
            set.loadFromFiles(logger);
        } catch (MergingException e) {
            gotException = true;
            assertEquals(new File(root, "values" + separator + "values.xml").getAbsolutePath() +
                    ": Error: Found item Attr/d_common_attr more than one time",
                    e.getMessage());
        }

        assertTrue("ResourceSet processing should have failed, but didn't", gotException);
        assertFalse(logger.getErrorMsgs().isEmpty());
    }

    @Test
    public void testBrokenSet4() throws Exception {
        File root = TestResources.getDirectory(getClass(), "/testData/resources/brokenSet4");

        ResourceSet set = createResourceSet();
        set.addSource(root);

        boolean gotException = false;
        RecordingLogger logger =  new RecordingLogger();
        try {
            set.loadFromFiles(logger);
        } catch (MergingException e) {
            gotException = true;
            assertEquals(new File(root, "values" + separator + "values.xml").getAbsolutePath() +
                    ":7:6: Error: The element type \"declare-styleable\" "
                    + "must be terminated by the matching end-tag \"</declare-styleable>\".",
                    e.getMessage());
        }

        assertTrue("ResourceSet processing should have failed, but didn't", gotException);
        assertFalse(logger.getErrorMsgs().isEmpty());
    }

    @Test
    public void testBrokenSetBadType() throws Exception {
        File root = TestResources.getDirectory(getClass(), "/testData/resources/brokenSetBadType");

        ResourceSet set = createResourceSet();
        set.addSource(root);

        boolean gotException = false;
        RecordingLogger logger =  new RecordingLogger();
        try {
            set.loadFromFiles(logger);
        } catch (MergingException e) {
            gotException = true;
            assertThat(e.getMessage()).contains("dimenot");
            assertThat(e.getMessage())
                    .contains(
                            new File(root, "values" + separator + "dimens.xml").getAbsolutePath());
        }

        assertTrue("ResourceSet processing should have failed, but didn't", gotException);
        assertFalse(logger.getErrorMsgs().isEmpty());
    }

    @Test
    public void testBrokenSetBadType2() throws Exception {
        File root = TestResources.getDirectory(getClass(), "/testData/resources/brokenSetBadType2");

        ResourceSet set = createResourceSet();
        set.addSource(root);

        boolean gotException = false;
        RecordingLogger logger =  new RecordingLogger();
        try {
            set.loadFromFiles(logger);
        } catch (MergingException e) {
            gotException = true;
            assertThat(e.getMessage()).contains("dimenot");
            assertThat(e.getMessage())
                    .contains(
                            new File(root, "values" + separator + "dimens.xml").getAbsolutePath());
        }

        assertTrue("ResourceSet processing should have failed, but didn't", gotException);
        assertFalse(logger.getErrorMsgs().isEmpty());
    }

    @Test
    public void testTrackSourcePositions() throws IOException, MergingException {
        File root = TestResources.getDirectory(getClass(), "/testData/resources/baseSet");

        // By default, track positions.
        ResourceSet resourceSet = createResourceSet();
        resourceSet.addSource(root);
        RecordingLogger logger = new RecordingLogger();
        resourceSet.loadFromFiles(logger);

        checkLogger(logger);
        String stringKey = "string/basic_string";
        List<ResourceMergerItem> resources = resourceSet.getDataMap().get(stringKey);
        assertNotNull(resources);
        assertFalse(resources.isEmpty());

        int extraOffset =  Files.toString(resources.get(0).getSourceFile().getFile(), Charsets.UTF_8)
                .contains("\r") ? 13 : 0;  // Account for \r on Windows
        assertEquals(new SourcePosition(13, 4, 529 + extraOffset, 13, 53, 578 + extraOffset),
                XmlUtils.getSourceFilePosition(resources.get(0).getValue()).getPosition());

        // Try without positions.
        resourceSet = createResourceSet();
        resourceSet.addSource(root);
        resourceSet.setTrackSourcePositions(false);
        logger = new RecordingLogger();
        resourceSet.loadFromFiles(logger);

        resources = resourceSet.getDataMap().get(stringKey);
        assertNotNull(resources);
        assertFalse(resources.isEmpty());
        assertEquals(SourceFilePosition.UNKNOWN,
                     XmlUtils.getSourceFilePosition(resources.get(0).getValue()));
    }

    static ResourceSet getBaseResourceSet() throws MergingException, IOException {
        File root = TestResources
                .getDirectory(ResourceSetTest.class, "/testData/resources/baseSet");

        ResourceSet resourceSet = createResourceSet();
        resourceSet.addSource(root);
        RecordingLogger logger =  new RecordingLogger();
        resourceSet.loadFromFiles(logger);

        checkLogger(logger);

        return resourceSet;
    }
}
