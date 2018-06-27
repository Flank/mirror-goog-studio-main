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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.testutils.TestResources;
import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.w3c.dom.Document;

/**
 */
public class ValueResourceParser2Test extends BaseTestCase {

    private static List<ResourceMergerItem> sResources = null;

    @Test
    public void testParsedResourcesByCount() throws Exception {
        List<ResourceMergerItem> resources = getParsedResources();

        assertEquals(28, resources.size());
    }

    @Test
    public void testParsedResourcesByName() throws Exception {
        List<ResourceMergerItem> resources = getParsedResources();
        Map<String, ResourceMergerItem> resourceMap =
                Maps.newHashMapWithExpectedSize(resources.size());
        for (ResourceMergerItem item : resources) {
            resourceMap.put(item.getKey(), item);
        }

        String[] resourceNames =
                new String[] {
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
                    "id/item_id",
                    "integer/integer",
                    "layout/layout_ref",
                    "plurals/plurals",
                    "plurals/plurals_with_bad_quantity"
                };

        for (String name : resourceNames) {
            assertNotNull(name, resourceMap.get(name));
        }
    }

    private static class ResourceFinder implements Predicate<ResourceMergerItem> {
        private final String myKeyToFind;

        ResourceFinder(String keyToFind) {
            myKeyToFind = keyToFind;
        }

        @Override
        public boolean apply(ResourceMergerItem input) {
            return input.getKey().equals(myKeyToFind);
        }
    }

    @Test
    public void testParsedResourceByValues() throws Exception {
        List<ResourceMergerItem> resources = getParsedResources();
        // Test tools:quantity parsing in plurals.
        ResourceMergerItem plurals =
                Iterables.find(resources, new ResourceFinder("plurals/plurals"));
        ResourceValue pluralsValue = plurals.getResourceValue();
        assertNotNull(pluralsValue);
        assertEquals("@string/two", pluralsValue.getValue());

        ResourceMergerItem pluralsWithBadQuantity =
                Iterables.find(resources, new ResourceFinder("plurals/plurals_with_bad_quantity"));
        ResourceValue pluralsValueBadQuantity = pluralsWithBadQuantity.getResourceValue();
        assertNotNull(pluralsValueBadQuantity);
        assertEquals("one", pluralsValueBadQuantity.getValue());

        // Test tools:index parsing in arrays.
        ResourceMergerItem stringArray =
                Iterables.find(resources, new ResourceFinder("array/string_array"));
        ResourceValue stringArrayValue = stringArray.getResourceValue();
        assertNotNull(stringArrayValue);
        assertEquals("GHI", stringArrayValue.getValue());

        ResourceMergerItem integerArray =
                Iterables.find(resources, new ResourceFinder("array/integer_array"));
        ResourceValue integerArrayValue = integerArray.getResourceValue();
        assertNotNull(integerArrayValue);
        assertEquals("3", integerArrayValue.getValue());
    }

    private static List<ResourceMergerItem> getParsedResources() throws MergingException {
        if (sResources == null) {
            File root = TestResources
                    .getDirectory(ValueResourceParser2Test.class, "/testData/resources/baseSet");
            File values = new File(root, "values");
            File valuesXml = new File(values, "values.xml");

            ValueResourceParser2 parser = new ValueResourceParser2(valuesXml, null, null);
            sResources = parser.parseFile();

            // create a fake resource file to allow calling ResourceMergerItem.getKey()
            //noinspection ResultOfObjectAllocationIgnored
            new ResourceFile(valuesXml, sResources, new FolderConfiguration());
        }

        return sResources;
    }

    @Test
    public void testUtfBom() throws IOException, MergingException {
        File file = File.createTempFile("testUtfBom", SdkConstants.DOT_XML);
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"wrap_content\"\n" +
                "    android:orientation=\"vertical\" >\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Button\" />\n" +
                "          some text\n" +
                "\n" +
                "</LinearLayout>\n";

        BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(file));
        OutputStreamWriter writer = new OutputStreamWriter(stream, Charsets.UTF_8);
        stream.write(0xef);
        stream.write(0xbb);
        stream.write(0xbf);
        writer.write(xml);
        writer.close();

        Document document = ValueResourceParser2.parseDocument(file, false);
        assertNotNull(document);
        assertNotNull(document.getDocumentElement());
        assertEquals("LinearLayout", document.getDocumentElement().getTagName());

        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    @Test
    public void testBoolItems() throws IOException, MergingException {
        // BOOL types weren't covered in the baseSet/values.xml, so test that too.
        File file = File.createTempFile("testBoolItems", SdkConstants.DOT_XML);
        String xml = "" +
                     "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                     "<resources>\n" +
                     "\n" +
                     "<bool name=\"truthy\"> true </bool>\n" +
                     "<item name=\"not_truthy\" type=\"bool\">false</item>\n" +
                     // The parser doesn't really validate if the value is only true or false.
                     "<bool name=\"other\">excluded middle</bool>\n" +
                     "\n" +
                     "</resources>\n";

        BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(file));
        OutputStreamWriter writer = new OutputStreamWriter(stream, Charsets.UTF_8);
        stream.write(0xef);
        stream.write(0xbb);
        stream.write(0xbf);
        writer.write(xml);
        writer.close();

        ValueResourceParser2 parser = new ValueResourceParser2(file, null, null);
        List<ResourceMergerItem> items = parser.parseFile();
        assertEquals(3, items.size());
        assertEquals(ResourceType.BOOL, items.get(0).getType());
        assertEquals("truthy", items.get(0).getName());
        assertEquals(ResourceType.BOOL, items.get(1).getType());
        assertEquals("not_truthy", items.get(1).getName());
        assertEquals(ResourceType.BOOL, items.get(2).getType());
        assertEquals("other", items.get(2).getName());
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    @Test
    public void testPublicTag() throws Exception {
        File file = File.createTempFile("testPublicTag", SdkConstants.DOT_XML);
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources><public /></resources>";
        Files.write(file.toPath(), xml.getBytes());

        ValueResourceParser2 parser = new ValueResourceParser2(file, null, null);
        List<ResourceMergerItem> items = parser.parseFile();
        ResourceMergerItem publicTag = Iterables.getOnlyElement(items);

        // Make sure the name is invalid, so it cannot conflict with anything the user would type.
        assertNotNull(
                ValueResourceNameValidator.getErrorText(publicTag.getName(), publicTag.getType()));

        ResourceValue resourceValue = publicTag.getResourceValue();
        assertNotNull(resourceValue);
        assertEquals(ResourceType.PUBLIC, resourceValue.getResourceType());
        assertEquals("", resourceValue.getValue());
    }
}
