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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.testutils.TestResources;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

/** Tests for {@link IdGeneratingResourceParser}. */
public class IdGeneratingResourceParserTest extends BaseTestCase {
    @Test
    public void testParseLayoutDocument() throws Exception {
        File root = TestResources.getDirectory(getClass(), "/testData/resources/idGenerating");
        File layout = new File(root, "layout");
        File layoutFile = new File(layout, "layout_for_id_scan.xml");

        IdGeneratingResourceParser parser =
                new IdGeneratingResourceParser(
                        layoutFile, "layout_for_id_scan", ResourceType.LAYOUT, null, null);
        ResourceMergerItem fileItem = parser.getFileResourceMergerItem();
        assertEquals("layout_for_id_scan", fileItem.getName());
        assertEquals(ResourceType.LAYOUT, fileItem.getType());

        List<ResourceMergerItem> idItems = parser.getIdResourceMergerItems();
        assertResourceItemsNames(
                idItems,
                "btn_title_refresh",
                "bug123032845",
                "header",
                "image",
                "imageButton",
                "imageView",
                "imageView2",
                "nonExistent",
                "noteArea",
                "styledView",
                "text2",
                "title_refresh_progress");
    }

    @Test
    public void testParseMenuDocument() throws Exception {
        File root = TestResources.getDirectory(getClass(), "/testData/resources/idGenerating");
        File menu = new File(root, "menu");
        File menuFile = new File(menu, "menu.xml");

        IdGeneratingResourceParser parser =
                new IdGeneratingResourceParser(menuFile, "menu", ResourceType.MENU, null, null);

        ResourceMergerItem fileItem = parser.getFileResourceMergerItem();
        assertEquals("menu", fileItem.getName());
        assertEquals(ResourceType.MENU, fileItem.getType());

        List<ResourceMergerItem> idItems = parser.getIdResourceMergerItems();
        assertResourceItemsNames(idItems, "item1", "group", "group_item1", "group_item2", "submenu", "submenu_item2");
    }

    @Test
    public void testParseDataBindingDocument() throws Exception {
        File root = TestResources.getDirectory(getClass(), "/testData/resources/idGenerating");
        File layout = new File(root, "layout");
        File layoutFile = new File(layout, "layout_with_databinding.xml");

        try {
            new IdGeneratingResourceParser(
                    layoutFile, "layout_with_databinding", ResourceType.LAYOUT, null, null);
            fail("Should have thrown exception");
        }
        catch (MergingException e) {
            assertEquals("Error: Does not handle data-binding files", e.getMessage());
        }
    }

    private static void assertResourceItemsNames(
            @NonNull Collection<? extends ResourceItem> idItems, @NonNull String... expected) {
        Set<String> idNames =
                idItems.stream()
                        .peek(id -> assertEquals(ResourceType.ID, id.getType()))
                        .map(ResourceItem::getName)
                        .collect(Collectors.toSet());
        assertThat(idNames).containsExactlyElementsIn(expected);
    }
}
