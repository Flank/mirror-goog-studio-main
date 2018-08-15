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

import static com.android.ide.common.rendering.api.ResourceNamespace.ANDROID;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static org.junit.Assert.*;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.testutils.TestResources;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class TestResourceRepositoryTest extends BaseTestCase {
    @Test
    public void testMergeByCount() throws Exception {
        TestResourceRepository repo = getResourceRepository();

        ResourceTable items = repo.getFullTable();

        assertEquals(6, items.get(RES_AUTO, ResourceType.DRAWABLE).size());
        assertEquals(1, items.get(RES_AUTO, ResourceType.RAW).size());
        assertEquals(4, items.get(RES_AUTO, ResourceType.LAYOUT).size());
        assertEquals(1, items.get(RES_AUTO, ResourceType.COLOR).size());
        assertEquals(7, items.get(RES_AUTO, ResourceType.STRING).size());
        assertEquals(1, items.get(RES_AUTO, ResourceType.STYLE).size());
        assertEquals(3, items.get(RES_AUTO, ResourceType.ARRAY).size());
        assertEquals(7, items.get(RES_AUTO, ResourceType.ATTR).size());
        assertEquals(1, items.get(RES_AUTO, ResourceType.STYLEABLE).size());
        assertEquals(2, items.get(RES_AUTO, ResourceType.DIMEN).size());
        assertEquals(1, items.get(RES_AUTO, ResourceType.ID).size());
        assertEquals(1, items.get(RES_AUTO, ResourceType.INTEGER).size());
        assertEquals(2, items.get(RES_AUTO, ResourceType.PLURALS).size());
    }

    @Test
    public void testMergedResourcesByName() throws Exception {
        TestResourceRepository repo = getResourceRepository();

        // use ? between type and qualifier because of declare-styleable
        verifyResourceExists(
                repo,
                "drawable/icon",
                "drawable?ldpi-v4/icon",
                "drawable/icon2",
                "drawable/patch",
                "drawable/color_drawable",
                "drawable/drawable_ref",
                "raw/foo",
                "layout/main",
                "layout/layout_ref",
                "layout/alias_replaced_by_file",
                "layout/file_replaced_by_alias",
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
                "dimen?sw600dp-v13/offset",
                "id/item_id",
                "integer/integer",
                "plurals/plurals",
                "plurals/plurals_with_bad_quantity");
    }

    @Test
    public void testBaseStringValue() throws Exception {
        TestResourceRepository repo = getResourceRepository();

        List<ResourceItem> itemList =
                repo.getResources(RES_AUTO, ResourceType.STRING, "basic_string");
        assertNotNull(itemList);
        assertEquals(1, itemList.size());

        ResourceValue value = itemList.get(0).getResourceValue();
        assertNotNull(value);

        assertEquals("overlay_string", value.getValue());
    }

    @Test
    public void testCdataStringValue() throws Exception {
        TestResourceRepository repo = getResourceRepository();

        List<ResourceItem> itemList =
                repo.getResources(RES_AUTO, ResourceType.STRING, "cdata_string");
        assertNotNull(itemList);
        assertEquals(1, itemList.size());

        ResourceValue value = itemList.get(0).getResourceValue();
        assertNotNull(value);

        assertEquals(
                "XXX<![CDATA[<html>not<br>\nxml]]>YYY<![CDATA[<a href=\"url://web.site\">link</a>]]>ZZZ",
                value.getRawXmlValue());
    }

    @Test
    public void testBaseStyledStringValue() throws Exception {
        TestResourceRepository repo = getResourceRepository();

        List<ResourceItem> itemList =
                repo.getResources(RES_AUTO, ResourceType.STRING, "styled_string");
        assertNotNull(itemList);
        assertEquals(1, itemList.size());

        ResourceValue value = itemList.get(0).getResourceValue();
        assertNotNull(value);

        assertEquals("Forgot your username or password?\nVisit google.com/accounts/recovery.",
                value.getValue());
    }

    @Test
    public void testBaseColorValue() throws Exception {
        TestResourceRepository repo = getResourceRepository();

        List<ResourceItem> itemList = repo.getResources(RES_AUTO, ResourceType.COLOR, "color");
        assertNotNull(itemList);
        assertEquals(1, itemList.size());

        ResourceValue value = itemList.get(0).getResourceValue();
        assertNotNull(value);

        assertEquals("#FFFFFFFF", value.getValue());
    }

    @Test
    public void testBaseLayoutAliasValue() throws Exception {
        TestResourceRepository repo = getResourceRepository();

        List<ResourceItem> itemList =
                repo.getResources(RES_AUTO, ResourceType.LAYOUT, "layout_ref");
        assertNotNull(itemList);
        assertEquals(1, itemList.size());

        ResourceValue value = itemList.get(0).getResourceValue();
        assertNotNull(value);

        assertEquals("@layout/ref", value.getValue());
    }

    @Test
    public void testBaseAttrValue() throws Exception {
        TestResourceRepository repo = getResourceRepository();

        List<ResourceItem> itemList = repo.getResources(RES_AUTO, ResourceType.ATTR, "flag_attr");
        assertNotNull(itemList);
        assertEquals(1, itemList.size());

        ResourceValue value = itemList.get(0).getResourceValue();
        assertNotNull(value);
        assertTrue(value instanceof AttrResourceValue);
        AttrResourceValue attrValue = (AttrResourceValue) value;

        Map<String, Integer> attrValues = attrValue.getAttributeValues();
        assertNotNull(attrValues);
        assertEquals(3, attrValues.size());

        Integer i = attrValues.get("normal");
        assertNotNull(i);
        assertEquals(Integer.valueOf(0), i);

        i = attrValues.get("bold");
        assertNotNull(i);
        assertEquals(Integer.valueOf(1), i);

        i = attrValues.get("italic");
        assertNotNull(i);
        assertEquals(Integer.valueOf(2), i);
    }

    @Test
    public void testBaseStyleValue() throws Exception {
        TestResourceRepository repo = getResourceRepository();

        List<ResourceItem> itemList = repo.getResources(RES_AUTO, ResourceType.STYLE, "style");
        assertNotNull(itemList);
        assertEquals(1, itemList.size());

        ResourceValue value = itemList.get(0).getResourceValue();
        assertNotNull(value);
        assertTrue(value instanceof StyleResourceValue);
        StyleResourceValue styleResourceValue = (StyleResourceValue) value;

        assertEquals("@android:style/Holo.Light", styleResourceValue.getParentStyleName());

        StyleItemResourceValue styleValue = styleResourceValue.getItem(ANDROID, "singleLine");
        assertNotNull(styleValue);
        assertEquals("true", styleValue.getValue());

        styleValue = styleResourceValue.getItem(ANDROID, "textAppearance");
        assertNotNull(styleValue);
        assertEquals("@style/TextAppearance.WindowTitle", styleValue.getValue());

        styleValue = styleResourceValue.getItem(ANDROID, "shadowColor");
        assertNotNull(styleValue);
        assertEquals("#BB000000", styleValue.getValue());

        styleValue = styleResourceValue.getItem(ANDROID, "shadowRadius");
        assertNotNull(styleValue);
        assertEquals("2.75", styleValue.getValue());

        styleValue = styleResourceValue.getItem(RES_AUTO, "foo");
        assertNotNull(styleValue);
        assertEquals("foo", styleValue.getValue());
    }

    @Test
    public void testUpdateWithBasicFiles() throws Exception {
        File root = getIncMergeRoot("basicFiles");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger(0);
        resourceMerger.loadFromBlob(fakeRoot, false /*incrementalState*/);
        checkSourceFolders(resourceMerger);

        List<ResourceSet> sets = resourceMerger.getDataSets();
        assertEquals(2, sets.size());

        // write the content in a repo.
        TestResourceRepository repo = new TestResourceRepository();
        repo.update(resourceMerger);

        // checks the initial state of the repo
        ResourceTable items = repo.getFullTable();
        ListMultimap<String, ResourceItem> drawables = items.get(RES_AUTO, ResourceType.DRAWABLE);
        assertNotNull("Drawable null check", drawables);
        assertEquals("Drawable size check", 6, drawables.size());
        verifyResourceExists(repo,
                "drawable/new_overlay",
                "drawable/removed",
                "drawable?ldpi/removed",
                "drawable/touched",
                "drawable/removed_overlay",
                "drawable/untouched");

        // Apply updates
        RecordingLogger logger =  new RecordingLogger();

        // ----------------
        // first set is the main one, no change here
        ResourceSet mainSet = sets.get(0);
        File mainBase = new File(root, "main");
        File mainDrawable = new File(mainBase, "drawable");
        File mainDrawableLdpi = new File(mainBase, "drawable-ldpi");

        // touched/removed files:
        File mainDrawableTouched = new File(mainDrawable, "touched.png");
        mainSet.updateWith(mainBase, mainDrawableTouched, FileStatus.CHANGED, logger);
        checkLogger(logger);

        File mainDrawableRemoved = new File(mainDrawable, "removed.png");
        mainSet.updateWith(mainBase, mainDrawableRemoved, FileStatus.REMOVED, logger);
        checkLogger(logger);

        File mainDrawableLdpiRemoved = new File(mainDrawableLdpi, "removed.png");
        mainSet.updateWith(mainBase, mainDrawableLdpiRemoved, FileStatus.REMOVED, logger);
        checkLogger(logger);

        // ----------------
        // second set is the overlay one
        ResourceSet overlaySet = sets.get(1);
        File overlayBase = new File(root, "overlay");
        File overlayDrawable = new File(overlayBase, "drawable");
        File overlayDrawableHdpi = new File(overlayBase, "drawable-hdpi");

        // new/removed files:
        File overlayDrawableNewOverlay = new File(overlayDrawable, "new_overlay.png");
        overlaySet.updateWith(overlayBase, overlayDrawableNewOverlay, FileStatus.NEW, logger);
        checkLogger(logger);

        File overlayDrawableRemovedOverlay = new File(overlayDrawable, "removed_overlay.png");
        overlaySet.updateWith(overlayBase, overlayDrawableRemovedOverlay, FileStatus.REMOVED,
                logger);
        checkLogger(logger);

        File overlayDrawableHdpiNewAlternate = new File(overlayDrawableHdpi, "new_alternate.png");
        overlaySet.updateWith(overlayBase, overlayDrawableHdpiNewAlternate, FileStatus.NEW, logger);
        checkLogger(logger);

        // validate for duplicates
        resourceMerger.validateDataSets();

        // check the new content.
        repo.update(resourceMerger);

        drawables = items.get(RES_AUTO, ResourceType.DRAWABLE);
        assertNotNull("Drawable null check", drawables);
        assertEquals("Drawable size check", 5, drawables.size());
        verifyResourceExists(repo,
                "drawable/new_overlay",
                "drawable/touched",
                "drawable/removed_overlay",
                "drawable/untouched",
                "drawable?hdpi-v4/new_alternate");
        checkRemovedItems(resourceMerger);
    }

    @Test
    public void testUpdateWithBasicValues() throws Exception {
        File root = getIncMergeRoot("basicValues");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger(0);
        resourceMerger.loadFromBlob(fakeRoot, false /*incrementalState*/);
        checkSourceFolders(resourceMerger);

        List<ResourceSet> sets = resourceMerger.getDataSets();
        assertEquals(2, sets.size());

        // write the content in a repo.
        TestResourceRepository repo = new TestResourceRepository();
        repo.update(resourceMerger);

        // checks the initial state of the repo
        ResourceTable items = repo.getFullTable();
        ListMultimap<String, ResourceItem> strings = items.get(RES_AUTO, ResourceType.STRING);
        assertNotNull("String null check", strings);
        assertEquals("String size check", 5, strings.size());
        verifyResourceExists(repo,
                "string/untouched",
                "string/touched",
                "string/removed",
                "string?en/removed",
                "string/new_overlay");

        // apply updates
        RecordingLogger logger = new RecordingLogger();

        // ----------------
        // first set is the main one, no change here
        ResourceSet mainSet = sets.get(0);
        File mainBase = new File(root, "main");
        File mainValues = new File(mainBase, "values");
        File mainValuesEn = new File(mainBase, "values-en");

        // touched file:
        File mainValuesTouched = new File(mainValues, "values.xml");
        mainSet.updateWith(mainBase, mainValuesTouched, FileStatus.CHANGED, logger);
        checkLogger(logger);

        // removed files
        File mainValuesEnRemoved = new File(mainValuesEn, "values.xml");
        mainSet.updateWith(mainBase, mainValuesEnRemoved, FileStatus.REMOVED, logger);
        checkLogger(logger);

        // ----------------
        // second set is the overlay one
        ResourceSet overlaySet = sets.get(1);
        File overlayBase = new File(root, "overlay");
        File overlayValues = new File(overlayBase, "values");
        File overlayValuesFr = new File(overlayBase, "values-fr");

        // new files:
        File overlayValuesNew = new File(overlayValues, "values.xml");
        overlaySet.updateWith(overlayBase, overlayValuesNew, FileStatus.NEW, logger);
        checkLogger(logger);

        File overlayValuesFrNew = new File(overlayValuesFr, "values.xml");
        overlaySet.updateWith(overlayBase, overlayValuesFrNew, FileStatus.NEW, logger);
        checkLogger(logger);

        // validate for duplicates
        resourceMerger.validateDataSets();

        // check the new content.
        repo.update(resourceMerger);

        strings = items.get(RES_AUTO, ResourceType.STRING);
        assertNotNull("String null check", strings);
        assertEquals("String size check", 4, strings.size());
        verifyResourceExists(repo,
                "string/untouched",
                "string/touched",
                "string/new_overlay",
                "string?fr/new_alternate");
        checkRemovedItems(resourceMerger);
    }

    @Test
    public void testUpdateWithBasicValues2() throws Exception {
        File root = getIncMergeRoot("basicValues2");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger(0);
        resourceMerger.loadFromBlob(fakeRoot, false /*incrementalState*/);
        checkSourceFolders(resourceMerger);

        List<ResourceSet> sets = resourceMerger.getDataSets();
        assertEquals(2, sets.size());

        // write the content in a repo.
        TestResourceRepository repo = new TestResourceRepository();
        repo.update(resourceMerger);

        // checks the initial state of the repo
        ResourceTable items = repo.getFullTable();
        ListMultimap<String, ResourceItem> strings = items.get(RES_AUTO, ResourceType.STRING);
        assertNotNull("String null check", strings);
        assertEquals("String size check", 2, strings.size());
        verifyResourceExists(repo,
                "string/untouched",
                "string/removed_overlay");

        // apply updates
        RecordingLogger logger = new RecordingLogger();

        // ----------------
        // first set is the main one, no change here

        // ----------------
        // second set is the overlay one
        ResourceSet overlaySet = sets.get(1);
        File overlayBase = new File(root, "overlay");
        File overlayValues = new File(overlayBase, "values");

        // new files:
        File overlayValuesNew = new File(overlayValues, "values.xml");
        overlaySet.updateWith(overlayBase, overlayValuesNew, FileStatus.REMOVED, logger);
        checkLogger(logger);

        // validate for duplicates
        resourceMerger.validateDataSets();

        // check the new content.
        repo.update(resourceMerger);

        strings = items.get(RES_AUTO, ResourceType.STRING);
        assertNotNull("String null check", strings);
        assertEquals("String size check", 2, strings.size());
        verifyResourceExists(repo,
                "string/untouched",
                "string/removed_overlay");
        checkRemovedItems(resourceMerger);
    }

    @Test
    public void testUpdateWithFilesVsValues() throws Exception {
        File root = getIncMergeRoot("filesVsValues");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger(0);
        resourceMerger.loadFromBlob(fakeRoot, false /*incrementalState*/);
        checkSourceFolders(resourceMerger);

        List<ResourceSet> sets = resourceMerger.getDataSets();
        assertEquals(1, sets.size());

        // write the content in a repo.
        TestResourceRepository repo = new TestResourceRepository();
        repo.update(resourceMerger);

        // checks the initial state of the repo
        ResourceTable items = repo.getFullTable();
        ListMultimap<String, ResourceItem> layouts = items.get(RES_AUTO, ResourceType.LAYOUT);
        assertNotNull("String null check", layouts);
        assertEquals("String size check", 3, layouts.size());
        verifyResourceExists(repo,
                "layout/main",
                "layout/file_replaced_by_alias",
                "layout/alias_replaced_by_file");

        // apply updates
        RecordingLogger logger = new RecordingLogger();

        // ----------------
        // Load the main set
        ResourceSet mainSet = sets.get(0);
        File mainBase = new File(root, "main");
        File mainValues = new File(mainBase, ResourceFolderType.VALUES.getName());
        File mainLayout = new File(mainBase, ResourceFolderType.LAYOUT.getName());

        // touched file:
        File mainValuesTouched = new File(mainValues, "values.xml");
        mainSet.updateWith(mainBase, mainValuesTouched, FileStatus.CHANGED, logger);
        checkLogger(logger);

        // new file:
        File mainLayoutNew = new File(mainLayout, "alias_replaced_by_file.xml");
        mainSet.updateWith(mainBase, mainLayoutNew, FileStatus.NEW, logger);
        checkLogger(logger);

        // removed file
        File mainLayoutRemoved = new File(mainLayout, "file_replaced_by_alias.xml");
        mainSet.updateWith(mainBase, mainLayoutRemoved, FileStatus.REMOVED, logger);
        checkLogger(logger);

        // validate for duplicates
        resourceMerger.validateDataSets();

        // check the new content.
        repo.update(resourceMerger);

        layouts = items.get(RES_AUTO, ResourceType.LAYOUT);
        assertNotNull("String null check", layouts);
        assertEquals("String size check", 3, layouts.size());
        verifyResourceExists(repo,
                "layout/main",
                "layout/file_replaced_by_alias",
                "layout/alias_replaced_by_file");
        checkRemovedItems(resourceMerger);
    }

    @Test
    public void testUpdateFromOldFile() throws Exception {
        File root = getIncMergeRoot("oldMerge");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger(0);
        assertFalse(resourceMerger.loadFromBlob(fakeRoot, false /*incrementalState*/));
    }

    private static void checkRemovedItems(DataMap<? extends DataItem> dataMap) {
        for (DataItem item : dataMap.getDataMap().values()) {
            if (item.isRemoved()) {
                fail("Removed item found: " + item);
            }
        }
    }

    /** Returns a merger with the baseSet and baseMerge content. */
    private static ResourceMerger getBaseResourceMerger() throws MergingException, IOException {
        File root =
                TestResources.getDirectory(
                        TestResourceRepositoryTest.class, "/testData/resources/baseMerge");

        ResourceSet res = ResourceSetTest.getBaseResourceSet();

        RecordingLogger logger = new RecordingLogger();

        ResourceSet overlay = new ResourceSet("overlay", RES_AUTO, null, true);
        overlay.addSource(new File(root, "overlay"));
        overlay.loadFromFiles(logger);

        checkLogger(logger);

        ResourceMerger resourceMerger = new ResourceMerger(0);
        resourceMerger.addDataSet(res);
        resourceMerger.addDataSet(overlay);

        return resourceMerger;
    }

    private static TestResourceRepository getResourceRepository()
            throws MergingException, IOException {
        ResourceMerger merger = getBaseResourceMerger();

        TestResourceRepository repo = new TestResourceRepository();

        repo.update(merger);
        return repo;
    }

    private static File getIncMergeRoot(String name) throws IOException {
        File root =
                TestResources.getDirectory(
                                TestResourceRepositoryTest.class,
                                "/testData/resources/incMergeData")
                        .getCanonicalFile();
        return new File(root, name);
    }

    private static void verifyResourceExists(
            TestResourceRepository repository, String... dataItemKeys) {
        ResourceTable items = repository.getFullTable();

        for (String resKey : dataItemKeys) {
            String type, name, qualifier = "";

            int pos = resKey.indexOf('/');
            if (pos != -1) {
                name = resKey.substring(pos + 1);
                type = resKey.substring(0, pos);
            } else {
                throw new IllegalArgumentException("Invalid key " + resKey);
            }

            // use ? as a qualifier delimiter because of
            // declare-styleable
            pos = type.indexOf('?');
            if (pos != -1) {
                qualifier = type.substring(pos + 1);
                type = type.substring(0, pos);
            }

            ResourceType resourceType = ResourceType.fromClassName(type);
            assertNotNull("Type check for " + resKey, resourceType);

            Multimap<String, ResourceItem> map = items.get(RES_AUTO, resourceType);
            assertNotNull("Map check for " + resKey, map);

            Collection<ResourceItem> list = map.get(name);
            int found = 0;
            for (ResourceItem resourceItem : list) {
                if (resourceItem.getName().equals(name)) {
                    if (qualifier.equals(resourceItem.getConfiguration().getQualifierString())) {
                        found++;
                    }
                }
            }

            assertEquals("Match for " + resKey, 1, found);
        }
    }
}
